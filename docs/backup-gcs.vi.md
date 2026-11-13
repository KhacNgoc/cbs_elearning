# Sao lưu lên Google Cloud Storage

Hướng dẫn cài đặt, vận hành và khôi phục bản sao lưu của hệ thống bằng `pg_dump`, snapshot
Qdrant và [restic](https://restic.net), với kho lưu trên Google Cloud Storage (GCS).
Khi chuyển sang Garage ở máy chủ riêng, xem [backup-garage.vi.md](backup-garage.vi.md): script
giữ nguyên, chỉ đổi cấu hình kho.

## 1. Tổng quan

### Sao lưu những gì

| Dữ liệu | Nguồn | Cách lấy |
|---|---|---|
| PostgreSQL | container `postgres` | `pg_dump -Fc` (dump nhất quán, không cần dừng ứng dụng) |
| Video, tài liệu | volume `training_uploads` | đọc trực tiếp volume (chỉ đọc) |
| Dữ liệu chatbot | Qdrant | API snapshot của từng collection |

Không sao lưu: model Ollama (tải lại được), log Loki, Grafana, pgAdmin. `.env.production`
và các secret cũng không nằm trong bản backup, phải cất riêng (mục 3.4).

### Luồng chạy

```
Máy chủ ứng dụng (cron 01:30 hằng ngày)
  scripts/backup/backup.sh run
   1. pg_dump  ──────────────► /var/backups/cbs-elearning/staging/postgres.dump
   2. Qdrant snapshot ───────► /var/backups/cbs-elearning/staging/qdrant/<collection>.snapshot
   3. container restic đọc staging + volume training_uploads
        ── mã hóa, chỉ gửi phần thay đổi ──►  gs://cbs-elearning-backup/restic
```

DB được dump trước, file upload được đọc ngay sau, nên mọi file mà DB tham chiếu đều có trong
cùng bản backup. Restic mã hóa dữ liệu bằng mật khẩu kho trước khi gửi; Google chỉ thấy dữ
liệu đã mã hóa. Video đã có trong kho sẽ không bị gửi lại ở các lần sau.

### File trong repo

| File | Vai trò |
|---|---|
| `scripts/backup/backup.sh` | `init`, `run`, `prune`, `check`, `snapshots`, `restic ...` |
| `scripts/backup/restore.sh` | khôi phục `postgres`, `uploads`, `qdrant` hoặc `all` |
| `scripts/backup/lib.sh` | phần dùng chung của hai script |
| `scripts/backup/backup.env.example` | mẫu cấu hình |
| `docker-compose.backup.yml` | container restic (`restic/restic:0.19.1`) gắn đúng volume của dự án |

Restic chạy trong container nên máy chủ không cần cài restic. Máy chủ cần: Linux, Docker
Compose 2.24.4 trở lên (như DEPLOYMENT.md), `bash`, `curl`, `flock` (gói `util-linux`).

Các ví dụ dưới đây giả định repo nằm ở `/opt/cbs_elearning`, cấu hình backup ở
`/etc/cbs-elearning-backup`. Đổi đường dẫn nếu khác.

## 2. Chuẩn bị trên Google Cloud

Làm một lần, trên máy có [gcloud CLI](https://cloud.google.com/sdk/docs/install) và tài khoản
có quyền quản trị project. Đặt biến cho gọn:

```bash
PROJECT_ID=your-gcp-project-id
BUCKET=cbs-elearning-backup          # tên bucket là duy nhất toàn cầu, đổi nếu đã có người dùng
REGION=asia-southeast1               # Singapore, gần Việt Nam nhất
SA=cbs-elearning-backup
gcloud config set project "$PROJECT_ID"
```

> **Quy định dữ liệu:** trước khi đưa dữ liệu học viên lên cloud nước ngoài, xác nhận với bộ
> phận an ninh thông tin / pháp chế. Dữ liệu được restic mã hóa phía máy chủ, khóa do mình giữ,
> nhưng vị trí lưu trữ vẫn là Singapore.

### 2.1 Tạo bucket

```bash
gcloud storage buckets create "gs://$BUCKET" \
  --location="$REGION" \
  --default-storage-class=STANDARD \
  --uniform-bucket-level-access \
  --public-access-prevention
```

Giữ lớp lưu trữ **STANDARD**, không đặt lifecycle chuyển sang Nearline/Coldline/Archive:
restic thường xuyên đọc và xóa file khi `prune`, các lớp lạnh sẽ bị tính phí xóa sớm và phí
đọc. Việc xóa bản cũ do `backup.sh prune` đảm nhiệm.

### 2.2 Bật soft delete (chống xóa nhầm, ransomware)

```bash
gcloud storage buckets update "gs://$BUCKET" --soft-delete-duration=30d
```

Object bị xóa (kể cả bị kẻ tấn công dùng key của máy chủ để xóa) vẫn khôi phục được trong
30 ngày (tối đa 90 ngày). Dung lượng ở trạng thái soft delete vẫn bị tính phí.

**Không dùng Bucket Lock / retention policy** với kho restic: restic tạo và xóa file khóa
(`locks/`) ở mỗi lần chạy, retention policy chặn việc xóa này và làm backup thất bại.

### 2.3 Tạo service account chỉ có quyền trên bucket này

```bash
gcloud iam service-accounts create "$SA" --display-name="CBS eLearning backup"
SA_EMAIL="$SA@$PROJECT_ID.iam.gserviceaccount.com"

gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" \
  --member="serviceAccount:$SA_EMAIL" --role=roles/storage.objectAdmin
gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" \
  --member="serviceAccount:$SA_EMAIL" --role=roles/storage.legacyBucketReader
```

Quyền chỉ áp lên bucket backup, không phải cả project. Service account này không tắt được
soft delete và không xóa được bucket.

### 2.4 Tạo key JSON

```bash
gcloud iam service-accounts keys create gcs-key.json --iam-account="$SA_EMAIL"
```

Chuyển `gcs-key.json` lên máy chủ ứng dụng an toàn (scp), rồi xóa bản trên máy cá nhân.

- Nếu bị từ chối với lỗi `iam.disableServiceAccountKeyCreation`: tổ chức đang chặn tạo key
  (mặc định với tổ chức GCP mới). Xin quản trị tổ chức ngoại lệ cho project này.
- Nếu máy chủ ứng dụng là VM trên Google Cloud: không cần key. Gắn service account trên cho VM
  và bỏ dòng `GOOGLE_APPLICATION_CREDENTIALS` trong cấu hình; restic tự lấy thông tin xác thực
  từ metadata của VM.

## 3. Cài đặt trên máy chủ ứng dụng

### 3.1 Thư mục cấu hình

```bash
sudo install -d -m 700 /etc/cbs-elearning-backup
sudo install -d -m 700 /var/backups/cbs-elearning/staging /var/backups/cbs-elearning/restore
sudo install -m 600 gcs-key.json /etc/cbs-elearning-backup/gcs-key.json && rm gcs-key.json
sudo install -m 600 /opt/cbs_elearning/scripts/backup/backup.env.example \
  /etc/cbs-elearning-backup/backup.env
```

### 3.2 Mật khẩu kho restic

```bash
sudo sh -c 'umask 077; openssl rand -base64 32 > /etc/cbs-elearning-backup/restic-password'
sudo cat /etc/cbs-elearning-backup/restic-password
```

> **Cất mật khẩu này vào password manager / két của đơn vị ngay bây giờ.** Mọi bản backup được
> mã hóa bằng nó; mất máy chủ mà không còn mật khẩu thì không khôi phục được gì, kể cả Google
> cũng không giải mã được.

### 3.3 Sửa `backup.env`

```bash
sudo nano /etc/cbs-elearning-backup/backup.env
```

Các dòng cần sửa:

```ini
RESTIC_REPOSITORY=gs:cbs-elearning-backup:/restic      # gs:<BUCKET>:/restic
GOOGLE_PROJECT_ID=your-gcp-project-id
GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcs-key.json
APP_ENV_FILE=/opt/cbs_elearning/.env.production
QDRANT_BACKUP_URL=http://127.0.0.1:6333                # để trống nếu không chạy profile chatbot
```

`/secrets/...` là đường dẫn bên trong container restic, trỏ tới `/etc/cbs-elearning-backup`.
Mỗi dòng dạng `KEY=value`, không dấu nháy, không khoảng trắng, không ký tự `$`.

`BACKUP_KEEP_DAILY/WEEKLY/MONTHLY` (mặc định 7/4/6) quyết định số bản giữ lại: 7 bản gần nhất
theo ngày, 4 theo tuần, 6 theo tháng.

### 3.4 Cất các secret không nằm trong backup

Bản backup không chứa `.env.production`, `backup.env`, `gcs-key.json`, `restic-password`.
Lưu bản sao của chúng ở password manager / két của đơn vị, tách khỏi máy chủ. Khôi phục trên
máy mới cần cả bốn.

## 4. Khởi tạo và chạy thử

```bash
cd /opt/cbs_elearning
sudo bash scripts/backup/backup.sh init        # tạo kho restic trong bucket, chỉ chạy một lần
sudo bash scripts/backup/backup.sh run         # backup đầu tiên: gửi toàn bộ video, có thể lâu
sudo bash scripts/backup/backup.sh snapshots   # phải thấy 1 snapshot, host cbs-elearning
```

Lần chạy đầu gửi toàn bộ dữ liệu; các lần sau chỉ gửi phần thay đổi. Kiểm tra nội dung:

```bash
sudo bash scripts/backup/backup.sh restic ls latest /data/staging
```

Phải có `/data/staging/postgres.dump` và (nếu có chatbot) `/data/staging/qdrant/*.snapshot`.

## 5. Lập lịch

Tạo `/etc/cron.d/cbs-elearning-backup`:

```cron
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
# Hằng ngày 01:30: backup
30 1 * * * root bash /opt/cbs_elearning/scripts/backup/backup.sh run   >> /var/log/cbs-elearning-backup.log 2>&1
# Chủ nhật 03:30: xóa bản cũ, sau đó kiểm tra 5% dữ liệu trong kho
30 3 * * 0 root bash /opt/cbs_elearning/scripts/backup/backup.sh prune >> /var/log/cbs-elearning-backup.log 2>&1
30 5 * * 0 root bash /opt/cbs_elearning/scripts/backup/backup.sh check >> /var/log/cbs-elearning-backup.log 2>&1
```

Các lệnh dùng chung một khóa (`flock`): nếu lệnh trước chưa xong, lệnh sau dừng và ghi log
thay vì chạy chồng. Nên xoay vòng log bằng logrotate (`/etc/logrotate.d/cbs-elearning-backup`):

```
/var/log/cbs-elearning-backup.log {
  weekly
  rotate 8
  compress
  missingok
}
```

## 6. Giám sát

- **Log:** `/var/log/cbs-elearning-backup.log`. Lỗi có dòng `THẤT BẠI`.
- **Cảnh báo khi backup không chạy:** đặt `BACKUP_HEALTHCHECK_URL` trong `backup.env` tới một
  dịch vụ kiểu "dead man's switch" (healthchecks.io, Uptime Kuma tự cài...). Script gọi
  `URL/start` khi bắt đầu, `URL` khi xong, `URL/fail` khi lỗi; dịch vụ báo động khi quá hạn
  mà không nhận được ping. Đây là cách duy nhất phát hiện cron ngừng chạy.
- **Dung lượng:** `backup.sh restic stats --mode raw-data` cho dung lượng thực trong kho;
  thư mục staging chiếm khoảng bằng kích thước dump DB + snapshot Qdrant.

## 7. Khôi phục

Liệt kê bản backup và chọn ID (hoặc dùng `latest`):

```bash
sudo bash scripts/backup/backup.sh snapshots
```

### 7.1 Trên hệ thống đang chạy

```bash
sudo bash scripts/backup/restore.sh <ID> postgres   # XÓA DB hiện tại, tạo lại từ bản dump
sudo bash scripts/backup/restore.sh <ID> uploads    # ghi đè file trong training_uploads
sudo bash scripts/backup/restore.sh <ID> qdrant     # ghi đè các collection chatbot
sudo bash scripts/backup/restore.sh <ID> all        # cả ba
```

Script hỏi xác nhận (thêm `--yes` để bỏ qua), dừng `backend` trong lúc khôi phục DB/uploads
rồi khởi động lại. Lưu ý:

- `postgres` xóa và tạo lại database: mọi thay đổi sau thời điểm backup bị mất. Bảng
  `flyway_schema_history` nằm trong bản dump; nếu code hiện tại có migration mới hơn, backend sẽ
  chạy migration đó khi khởi động.
- `uploads` mặc định không xóa file mới hơn bản backup (an toàn khi chỉ cần lấy lại file bị
  mất). Thêm `--delete` để thư mục giống hệt bản backup, ví dụ
  `restore.sh <ID> all --delete`: file upload sau thời điểm backup sẽ bị xóa.
- Khôi phục DB về thời điểm cũ mà giữ uploads mới thì chỉ thừa file, không lỗi. Ngược lại
  (uploads cũ, DB mới) sẽ có bài học trỏ tới video không tồn tại: khi khôi phục cả hai, dùng
  `all` với cùng một ID.

### 7.2 Trên máy chủ mới (mất máy chủ cũ)

1. Cài Docker, clone repo vào `/opt/cbs_elearning`, đặt lại `.env.production` từ két.
2. Làm lại mục 3.1–3.3 với `gcs-key.json`, `restic-password`, `backup.env` từ két. **Không chạy
   `backup.sh init`**: kho đã tồn tại.
3. Kiểm tra truy cập kho: `sudo bash scripts/backup/backup.sh snapshots`.
4. Khởi động database (và Qdrant nếu dùng chatbot):

   ```bash
   cd /opt/cbs_elearning
   docker compose --env-file .env.production up -d postgres
   docker compose --env-file .env.production --profile chatbot up -d qdrant   # nếu có chatbot
   ```

5. Khôi phục: `sudo bash scripts/backup/restore.sh latest all --yes`. Bước dừng backend sẽ báo
   không có gì để dừng, không sao.
6. Khởi động toàn bộ theo DEPLOYMENT.md. Đăng nhập, mở một bài học có video, thử chatbot.
7. Bật lại cron (mục 5).

## 8. Diễn tập khôi phục (hằng tháng)

Backup chưa thử khôi phục thì chưa biết có dùng được không. Mỗi tháng, trên một máy thử (không
phải production): làm mục 7.2 với bản `latest`, đếm số bản ghi một vài bảng chính và mở thử
vài video, ghi lại ngày diễn tập, thời gian khôi phục và kết quả. Không chạy `backup.sh run`
trên máy thử: nó sẽ ghi bản backup của máy thử vào cùng kho.

## 9. Xử lý sự cố

| Triệu chứng | Cách xử lý |
|---|---|
| `repository is already locked` | Một lần chạy trước bị ngắt. Chắc chắn không còn backup nào đang chạy rồi `backup.sh restic unlock`. |
| `403` / `AccessDenied` từ GCS | Kiểm tra service account có 2 role ở mục 2.3 trên đúng bucket, key còn hiệu lực, `GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcs-key.json`. |
| `Fatal: wrong password or no key found` | `restic-password` không khớp với kho. Lấy lại từ két. |
| Lỗi `pg_dump` / `service "postgres" is not running` | Container postgres không chạy, hoặc `APP_ENV_FILE` sai, hoặc stack chạy với `COMPOSE_PROJECT_NAME` khác (thêm dòng đó vào `backup.env`). |
| Lỗi curl tới `127.0.0.1:6333` | Qdrant không chạy. Nếu không dùng chatbot, để trống `QDRANT_BACKUP_URL`. |
| `check` báo lỗi dữ liệu | Không chạy `prune`. Xem [hướng dẫn sửa kho của restic](https://restic.readthedocs.io/en/stable/077_troubleshooting.html) và tạo bản backup mới ngay. |

## 10. Chi phí

Các khoản GCS tính phí: dung lượng lưu (STANDARD, gồm cả dung lượng ở trạng thái soft delete),
số thao tác ghi/đọc (nhỏ, vì restic gom dữ liệu thành file lớn) và băng thông tải ra khi khôi
phục hoặc khi chuyển kho sang nơi khác. Tải lên không tính phí. Ước tính theo dung lượng
`backup.sh restic stats --mode raw-data` với
[bảng giá GCS](https://cloud.google.com/storage/pricing).
