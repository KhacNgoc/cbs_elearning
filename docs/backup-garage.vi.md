# Chuyển kho sao lưu sang Garage trên máy chủ riêng

Tài liệu này dùng khi chuyển kho restic từ Google Cloud Storage
([backup-gcs.vi.md](backup-gcs.vi.md)) sang [Garage](https://garagehq.deuxfleurs.fr/), phần
mềm lưu trữ tương thích S3 mã nguồn mở (AGPLv3), chạy trên một máy chủ backup riêng. Script
backup/khôi phục, lịch cron, giám sát và quy trình khôi phục trên máy chủ ứng dụng **giữ
nguyên**; chỉ thay đổi nơi lưu kho và cấu hình `backup.env`.

```
Máy chủ ứng dụng                              Máy chủ backup (máy khác)
  backup.sh run                                ┌──────────────────────────────────────┐
    restic ── HTTPS :3900, S3 API ──────────►  │ nginx (TLS) ─► Garage (S3, 1 node)   │
                                               │                                      │
                                               │ Btrfs RAID1 trên 2 đĩa:              │
                                               │   /srv/garage/store   (meta + data)  │
                                               │   /srv/garage/.snapshots             │
                                               │     snapshot chỉ-đọc hằng ngày,      │
                                               │     giữ 30 ngày (chống xóa kho)      │
                                               └──────────────────────────────────────┘
```

## 0. Khác biệt cần biết so với GCS

Theo [bảng tương thích S3 của Garage](https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/):

| Tính năng | Garage | Thay thế trong tài liệu này |
|---|---|---|
| TLS (HTTPS) | không có, cần reverse proxy | nginx phía trước (mục 2.4) |
| Versioning, Object Lock | không có | **snapshot Btrfs chỉ-đọc** trên máy chủ backup (mục 2.6) |
| Bucket policy, IAM | không có; quyền theo cặp key–bucket (`--read`, `--write`, `--owner`) | key riêng chỉ có `--read --write` trên đúng bucket backup |
| Nhân bản dữ liệu | cần ≥ 3 node; 1 node (`replication_factor = 1`) Garage chỉ khuyến nghị cho thử nghiệm | Btrfs RAID1 hai đĩa cho dự phòng đĩa; xem mục 7 nếu cần nhiều node |

**Vì sao cần snapshot Btrfs:** key backup có quyền ghi thì xóa được object (restic cần xóa
file khóa). Nếu máy chủ ứng dụng bị chiếm, kẻ tấn công có thể xóa toàn bộ kho qua S3. Snapshot
Btrfs được tạo và chỉ xóa được bởi root của máy chủ backup, máy chủ ứng dụng không có quyền
gì trên đó, nên vẫn khôi phục được kho về trạng thái của tối đa 30 ngày trước.

## 1. Yêu cầu máy chủ backup

- **Một máy khác** với máy chủ ứng dụng (khác máy vật lý/VM host, tốt nhất khác phòng máy).
- Linux 64-bit (Ubuntu 22.04/24.04, Debian 12...), Docker Compose, `openssl`, `btrfs-progs`.
- **Hai đĩa dữ liệu cùng dung lượng** (ví dụ `/dev/sdb`, `/dev/sdc`) dùng cho Btrfs RAID1. Dung
  lượng mỗi đĩa tối thiểu gấp 2 lần dung lượng kho restic hiện tại
  (`backup.sh restic stats --mode raw-data`): snapshot giữ lại dữ liệu đã bị `prune` thêm 30
  ngày, và dung lượng video sẽ tăng.
- Mạng nội bộ tới máy chủ ứng dụng; tốc độ ảnh hưởng thời gian backup lần đầu.
- **Không ai từ máy chủ ứng dụng có SSH/sudo vào máy chủ backup.** Đây là điều kiện để snapshot
  thực sự an toàn.

Ví dụ dưới đây dùng tên máy `backup-server.internal`, IP `10.0.0.20`; máy chủ ứng dụng
`10.0.0.10`. Thay bằng giá trị thật.

## 2. Cài đặt Garage

### 2.1 Đĩa Btrfs RAID1

> Lệnh `mkfs` **xóa toàn bộ dữ liệu** trên hai đĩa. Kiểm tra đúng tên đĩa bằng `lsblk` trước.

```bash
sudo apt install -y btrfs-progs
lsblk
sudo mkfs.btrfs -L garage -m raid1 -d raid1 /dev/sdb /dev/sdc
sudo mkdir -p /srv/garage
echo 'LABEL=garage /srv/garage btrfs defaults,noatime 0 0' | sudo tee -a /etc/fstab
sudo mount /srv/garage

# Toàn bộ dữ liệu Garage nằm trong MỘT subvolume để snapshot meta và data cùng một thời điểm.
sudo btrfs subvolume create /srv/garage/store
sudo mkdir -p /srv/garage/store/meta /srv/garage/store/data /srv/garage/.snapshots
sudo btrfs filesystem usage /srv/garage
```

Btrfs kiểm tra checksum mọi khối; với RAID1, khối hỏng trên một đĩa được sửa từ đĩa còn lại.

### 2.2 Chép file cấu hình

Thư mục `scripts/backup/garage` của repo gồm `docker-compose.yml`, `garage.toml`,
`garage.env.example`, `nginx.conf`, `btrfs-snapshot.sh`. Chép sang máy chủ backup:

```bash
# Từ máy có repo:
scp -r scripts/backup/garage backup-server.internal:/tmp/
# Trên máy chủ backup:
sudo install -d -m 700 /opt/garage-backup
sudo cp -r /tmp/garage/* /opt/garage-backup/ && rm -r /tmp/garage
cd /opt/garage-backup
```

`garage.toml` không chứa secret, dùng được ngay. Các lựa chọn trong đó: `db_engine = "sqlite"`
(Garage đánh giá chịu mất điện tốt hơn lmdb), bật `fsync`, `replication_factor = 1`, tắt nén
(dữ liệu restic đã nén và mã hóa), metadata tự snapshot 6 giờ một lần.

### 2.3 Secret

```bash
sudo cp garage.env.example garage.env && sudo chmod 600 garage.env
openssl rand -hex 32       # GARAGE_RPC_SECRET
openssl rand -base64 32    # GARAGE_ADMIN_TOKEN
openssl rand -base64 32    # GARAGE_METRICS_TOKEN
sudo nano garage.env
```

Lưu `garage.env` vào két của đơn vị.

### 2.4 Chứng chỉ TLS

Garage không tự làm HTTPS; nginx trong `docker-compose.yml` nhận HTTPS ở cổng 3900. Nếu đơn vị
có CA nội bộ, xin chứng chỉ cho `backup-server.internal` và đặt vào `certs/public.crt`,
`certs/private.key`. Nếu chưa có, tạo chứng chỉ tự ký (10 năm); SAN phải chứa đúng tên/IP mà
máy chủ ứng dụng dùng để kết nối:

```bash
sudo install -d -m 700 certs
sudo openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 -nodes \
  -keyout certs/private.key -out certs/public.crt \
  -subj "/CN=backup-server.internal" \
  -addext "subjectAltName=DNS:backup-server.internal,IP:10.0.0.20"
```

`certs/public.crt` sẽ được chép sang máy chủ ứng dụng ở mục 3.

### 2.5 Khởi động và tạo bucket, key

```bash
cd /opt/garage-backup
echo 'GARAGE_LISTEN_ADDR=10.0.0.20' | sudo tee .env   # chỉ lắng nghe trên card mạng nội bộ
sudo docker compose up -d
sudo docker compose logs garage | tail -20
```

Image Garage không có shell; CLI chạy bằng `docker exec`. Đặt hàm tắt:

```bash
garage() { sudo docker exec -i garage-backup /garage "$@"; }
garage status          # cột đầu là node ID, trạng thái NO ROLE ASSIGNED
```

Gán vai trò cho node (chỉ làm một lần). Với một node, dung lượng `-c` chỉ mang tính khai báo:

```bash
garage layout assign -z dc1 -c 1T <node-id>     # vài ký tự đầu của node ID là đủ
garage layout show                              # ghi lại số version được đề xuất
garage layout apply --version <version>          # thường là 1 ở lần đầu
garage status                                   # node có zone dc1
```

Bucket và key cho restic:

```bash
garage bucket create cbs-elearning-backup
garage key create cbs-backup        # ghi lại "Key ID" (GK...) và "Secret key"
garage bucket allow --read --write cbs-elearning-backup --key cbs-backup
garage bucket info cbs-elearning-backup
```

Không cấp `--owner`: key backup không cần quản lý bucket. `garage key info` mặc định ẩn
secret (in `(redacted)`); muốn xem lại secret phải dùng
`garage key info --show-secret cbs-backup`. Đừng chép nhầm chữ `(redacted)` vào `backup.env`:
restic sẽ báo `Access Denied` và log Garage báo `Invalid signature`.

### 2.6 Snapshot Btrfs hằng ngày

```bash
sudo install -m 700 /opt/garage-backup/btrfs-snapshot.sh /usr/local/sbin/garage-btrfs-snapshot
sudo /usr/local/sbin/garage-btrfs-snapshot          # chạy thử
sudo btrfs subvolume list /srv/garage
```

Tạo `/etc/cron.d/garage-btrfs-snapshot`:

```cron
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
# 06:30 hằng ngày, sau khi backup đêm (01:30) và prune Chủ nhật (03:30) đã xong
30 6 * * * root garage-btrfs-snapshot >> /var/log/garage-btrfs-snapshot.log 2>&1
# Ngày 1 hằng tháng: Btrfs đọc toàn bộ đĩa, sửa khối hỏng từ đĩa còn lại
0 2 1 * * root btrfs scrub start -B /srv/garage >> /var/log/garage-btrfs-snapshot.log 2>&1
```

Số ngày giữ đặt bằng biến `GARAGE_SNAPSHOT_KEEP_DAYS` (mặc định 30). Snapshot được chụp khi
Garage đang chạy nên ở trạng thái như sau mất điện; sqlite và Garage chịu được trạng thái này,
và vì chụp ngoài giờ backup nên không có ghi dở.

### 2.7 Tường lửa

```bash
sudo ufw default deny incoming
sudo ufw allow OpenSSH
sudo ufw allow from 10.0.0.10 to any port 3900 proto tcp   # chỉ máy chủ ứng dụng
sudo ufw enable
```

Docker tự thêm luật iptables cho cổng publish và có thể vượt qua ufw; `GARAGE_LISTEN_ADDR` ở
mục 2.5 giới hạn card mạng, và nên chặn thêm ở firewall mạng chỉ cho `10.0.0.10` tới
`10.0.0.20:3900`. Cổng RPC (3901) và admin (3903) của Garage không publish ra ngoài.

### 2.8 Giám sát máy chủ backup

- Dung lượng: `sudo btrfs filesystem usage /srv/garage`; cảnh báo khi trên 80%.
- Sức khỏe đĩa: `sudo btrfs device stats /srv/garage` (mọi bộ đếm phải bằng 0) và kết quả scrub
  trong log.
- Garage: `garage status`, `sudo docker compose logs garage`.
- Cảnh báo backup thất bại vẫn do `BACKUP_HEALTHCHECK_URL` trên máy chủ ứng dụng đảm nhiệm.

## 3. Cấu hình máy chủ ứng dụng

Chép chứng chỉ (bỏ qua nếu chứng chỉ do CA nội bộ mà máy chủ đã tin):

```bash
scp backup-server.internal:/opt/garage-backup/certs/public.crt /tmp/garage-ca.crt
sudo install -m 600 /tmp/garage-ca.crt /etc/cbs-elearning-backup/garage-ca.crt && rm /tmp/garage-ca.crt
curl --cacert /etc/cbs-elearning-backup/garage-ca.crt -s -o /dev/null -w '%{http_code}\n' \
  https://backup-server.internal:3900/
```

Lệnh `curl` phải in ra một mã HTTP (thường là `403`, vì không ký); lỗi chứng chỉ hoặc kết nối
nghĩa là TLS/tường lửa chưa đúng.

Sửa `/etc/cbs-elearning-backup/backup.env`: thêm `#` vào 3 dòng GCS, bỏ `#` ở 5 dòng Garage:

```ini
# RESTIC_REPOSITORY=gs:cbs-elearning-backup:/restic
# GOOGLE_PROJECT_ID=...
# GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcs-key.json
RESTIC_REPOSITORY=s3:https://backup-server.internal:3900/cbs-elearning-backup
AWS_ACCESS_KEY_ID=<Key ID, dạng GK...>
AWS_SECRET_ACCESS_KEY=<Secret key>
AWS_DEFAULT_REGION=garage
RESTIC_CACERT=/secrets/garage-ca.crt
```

`AWS_DEFAULT_REGION` phải trùng `s3_region` trong `garage.toml`. Giữ nguyên
`RESTIC_PASSWORD_FILE` và các dòng còn lại. Bước tiếp theo phụ thuộc cách chuyển dữ liệu ở
mục 4.

## 4. Chuyển dữ liệu từ GCS

Chọn một trong hai cách.

| | A. Kho mới, giữ GCS đến hết hạn | B. Chép lịch sử sang Garage |
|---|---|---|
| Làm | `init` kho mới, backup đầy đủ lần đầu lên Garage | `restic copy` toàn bộ snapshot từ GCS |
| Lịch sử cũ | vẫn ở GCS, khôi phục bằng cấu hình GCS | có ngay trên Garage |
| Phí tải ra từ GCS | không | có (bằng dung lượng kho) |
| Phí lưu trữ GCS | trả thêm tới khi bản cũ nhất hết giá trị (tới 6 tháng với cấu hình mặc định) | dừng ngay sau khi chép |

Thông thường chọn **B** nếu kho nhỏ hoặc cần lịch sử theo tháng; chọn **A** nếu kho lớn và chỉ
cần các bản gần đây.

Trước khi làm, tắt cron backup để không chạy chồng:
`sudo mv /etc/cron.d/cbs-elearning-backup /root/`.

### Cách A

```bash
cd /opt/cbs_elearning
sudo bash scripts/backup/backup.sh init
sudo bash scripts/backup/backup.sh run
sudo bash scripts/backup/backup.sh snapshots
```

Khi cần một bản cũ trên GCS: tạm bỏ `#` ở 3 dòng GCS và thêm `#` vào 5 dòng Garage, khôi phục,
rồi đổi lại. Hủy GCS ở mục 5 khi không cần lịch sử cũ nữa.

### Cách B

Tạm thời thêm vào `backup.env` hai dòng chỉ tới kho GCS cũ, và bỏ `#` ở hai dòng Google để
restic còn xác thực được với GCS:

```ini
RESTIC_FROM_REPOSITORY=gs:cbs-elearning-backup:/restic
RESTIC_FROM_PASSWORD_FILE=/secrets/restic-password
GOOGLE_PROJECT_ID=...
GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcs-key.json
```

```bash
cd /opt/cbs_elearning
# Tạo kho Garage với cùng tham số chia khối như kho cũ, để dữ liệu chép sang được loại trùng.
sudo bash scripts/backup/backup.sh restic init --copy-chunker-params
sudo bash scripts/backup/backup.sh restic copy
sudo bash scripts/backup/backup.sh snapshots      # phải thấy đủ các snapshot cũ
sudo bash scripts/backup/backup.sh check
```

`restic copy` chạy lại được nếu bị ngắt; snapshot đã chép sẽ được bỏ qua. Xong thì xóa
`RESTIC_FROM_*` và thêm lại `#` cho hai dòng Google trong `backup.env`.

### Sau khi chuyển (cả hai cách)

```bash
sudo bash scripts/backup/backup.sh run
sudo bash scripts/backup/backup.sh snapshots
sudo mv /root/cbs-elearning-backup /etc/cron.d/       # bật lại cron
```

Rồi **diễn tập khôi phục** từ Garage trên máy thử theo mục 7.2 và 8 của
[backup-gcs.vi.md](backup-gcs.vi.md) trước khi hủy GCS. Cập nhật két: thêm `garage-ca.crt`,
Key ID và Secret key của `cbs-backup`, `garage.env`; `restic-password` giữ nguyên.

## 5. Ngừng dùng GCS

Chỉ làm sau khi đã diễn tập khôi phục thành công từ Garage (và, với cách A, khi không còn cần
lịch sử trên GCS):

```bash
# Trên máy có gcloud
gcloud storage buckets update "gs://$BUCKET" --clear-soft-delete   # nếu không cần giữ 30 ngày
gcloud storage rm -r "gs://$BUCKET"                                # xóa toàn bộ object và bucket
gcloud iam service-accounts delete "$SA@$PROJECT_ID.iam.gserviceaccount.com"
```

Xóa bucket là **không hoàn tác được** khi đã tắt soft delete. Trên máy chủ ứng dụng, xóa
`/etc/cbs-elearning-backup/gcs-key.json` và các dòng GCS còn lại trong `backup.env`.

## 6. Vận hành

### 6.1 Việc thường kỳ

Trên máy chủ ứng dụng không đổi so với GCS: cron `run`/`prune`/`check`, log, healthcheck, diễn
tập hằng tháng. Trên máy chủ backup: theo dõi dung lượng, `device stats`, kết quả scrub (mục
2.8), log snapshot, gia hạn chứng chỉ trước khi hết hạn, và cập nhật Garage khi có bản vá
(mục 6.4).

### 6.2 Khi kho restic bị xóa hoặc phá hỏng

Dùng snapshot Btrfs để đưa toàn bộ dữ liệu Garage về một ngày trước sự cố. Trên máy chủ backup:

```bash
sudo btrfs subvolume list /srv/garage | grep snapshots     # chọn snapshot trước sự cố
cd /opt/garage-backup
sudo docker compose down
# Giữ bản hiện tại để điều tra, không xóa.
sudo mv /srv/garage/store /srv/garage/store-broken-$(date +%Y%m%d)
# Tạo bản ghi được từ snapshot chỉ-đọc.
sudo btrfs subvolume snapshot /srv/garage/.snapshots/store-YYYYMMDD-HHMMSS /srv/garage/store
sudo docker compose up -d
```

Trên máy chủ ứng dụng: `backup.sh snapshots` và `backup.sh check` phải chạy tốt, sau đó khôi
phục như bình thường. Nếu nghi máy chủ ứng dụng bị chiếm: tạo key Garage mới, xóa key cũ
(`garage key delete cbs-backup --yes`), đổi mọi secret của máy chủ ứng dụng. Khi đã ổn, xóa
`store-broken-*` bằng `btrfs subvolume delete`.

Mọi bản backup restic tạo sau thời điểm snapshot được chọn sẽ không còn; bản backup đêm tiếp
theo sẽ bù lại.

### 6.3 Khi một đĩa hỏng

Btrfs RAID1 vẫn đọc được từ đĩa còn lại. Thay đĩa càng sớm càng tốt:

```bash
sudo btrfs device stats /srv/garage                 # xác định đĩa lỗi
sudo btrfs filesystem show /srv/garage              # devid của đĩa lỗi
sudo btrfs replace start <devid-lỗi> /dev/sdd /srv/garage   # /dev/sdd: đĩa mới
sudo btrfs replace status /srv/garage
```

Nếu đĩa lỗi đã mất hẳn và hệ thống không mount được, mount với `-o degraded` rồi `replace`.
Xem [tài liệu Btrfs](https://btrfs.readthedocs.io/en/latest/btrfs-replace.html) trước khi làm.

### 6.4 Cập nhật Garage

Xem [ghi chú phát hành](https://git.deuxfleurs.fr/Deuxfleurs/garage/releases) trước, nhất là
khi lên phiên bản chính mới (có thể cần chuyển đổi metadata). Chụp snapshot trước, rồi đổi tag
image trong `docker-compose.yml`:

```bash
sudo /usr/local/sbin/garage-btrfs-snapshot
cd /opt/garage-backup
sudo nano docker-compose.yml          # dxflrs/garage:v2.5.0 -> tag mới
sudo docker compose pull && sudo docker compose up -d
garage status
```

Luôn ghim tag cụ thể, không dùng `latest`.

## 7. Khi cần độ an toàn cao hơn

- **Bản sao thứ hai ngoài site (3-2-1):** dùng `restic copy` định kỳ từ kho Garage sang một kho
  khác (một Garage ở site khác, hoặc lại GCS), hoặc giữ song song GCS với `BACKUP_KEEP_*` nhỏ.
- **Garage nhiều node:** 3 máy ở 3 vị trí, `replication_factor = 3`, mỗi object có 3 bản trên 3
  zone. Xem
  [hướng dẫn triển khai cụm](https://garagehq.deuxfleurs.fr/documentation/cookbook/real-world/).
  Phía máy chủ ứng dụng không đổi, chỉ trỏ `RESTIC_REPOSITORY` tới endpoint của cụm. Snapshot
  Btrfs vẫn cần trên từng node để chống xóa.
