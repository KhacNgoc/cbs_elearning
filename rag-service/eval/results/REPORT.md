# Đánh giá truy xuất: kết quả và đề xuất

Đo ngày 04/11/2026 trên 169 chunk đang chạy thật của tài liệu khóa CIF và GL. Không có mặc định
production nào bị thay đổi.

> **Cập nhật:** đề xuất này đã được áp dụng, service giờ dùng `bge-m3` ở ngưỡng 0,40. Các số liệu dưới
> đây là lúc so sánh: `current` trong các bảng là cấu hình cũ, nay có tên `nomic` trong script. Tên
> `current_k5` và `current_t50` nay là `nomic_k5` và `nomic_t50`.

## Đề xuất

**Chuyển mô hình embedding sang `bge-m3` với ngưỡng khoảng 0,40, sau khi hoàn thành các việc chuyển
đổi nêu bên dưới. Chưa nên thêm reranker.**

| End-to-end, dùng `qwen3:4b` thật | Hiện tại | `bge-m3` ở 0,40 | Thay đổi |
|---|---|---|---|
| Chunk mong đợi có trong prompt | 53% | 77% | +25 / -2 câu, p < 0,001 |
| Trả lời có trích dẫn chunk mong đợi | 53% | 77% | +25 / -2 câu, p < 0,001 |
| Câu trả lời được nhưng bị từ chối | 23% | 12% | giảm khoảng một nửa |
| Trả lời từ chunk sai | 24% | 12% | giảm khoảng một nửa |
| Câu ngoài phạm vi vẫn được trả lời | 0/16 | 0/16 | không đổi |
| Câu ngoài phạm vi bị chặn trước khi tới mô hình | 38% | 62% | ít lượt gọi mô hình hơn |
| Độ trễ p50, tất cả câu hỏi | 1.626 ms | 2.135 ms | +509 ms |
| Độ trễ p50, chỉ các câu được trả lời | 1.960 ms | 2.328 ms | +368 ms |
| Độ trễ truy xuất p50 | 17 ms | 25 ms | +8 ms |

Phần lớn độ trễ tăng thêm nằm ở bước sinh câu trả lời, không phải ở truy xuất. Bước embedding câu
hỏi chỉ thêm khoảng 8 ms. `bge-m3` chiếm khoảng 0,69 GB trong Ollama, so với 0,34 GB của
`nomic-embed-text`.

Kết quả ủng hộ giả thuyết mô hình embedding là nguyên nhân chính: chỉ đổi mô hình đã đưa chunk mong
đợi vào prompt ở nhiều hơn 25 câu so với hiện tại, và riêng chất lượng xếp hạng (hit@k không tính
ngưỡng) tăng từ 55% lên 82%.

**"Khi nào phải KYC lại?"** giờ tìm ra `CIF-20 › Câu 5` và trả lời "KYC phải được cập nhật định kỳ
và khi có sự kiện thay đổi/rủi ro." Chunk mong đợi xếp hạng 2.

Hành vi hiện tại của câu này còn tệ hơn từ chối. Khi đang mở khóa học, service đang chạy trả lời từ
`6.2. eKYC`, kèm những khẳng định mà chunk đó không hỗ trợ ("khi ngân hàng yêu cầu định danh lại theo
quy định của pháp luật"). Khi không mở khóa học nào, nó từ chối. Cả hai trường hợp đều đã được xác
nhận trên service đang chạy.

## Vì sao không chọn các phương án khác

- **Reranker trên `bge-m3`:** tỷ lệ trúng đạt 85%, tức +10 / -2 câu so với `bge-m3` ở 0,40
  (p = 0,039). Nếu chỉ tính câu trả lời có trích dẫn, mức lợi là +8 / -2 (p = 0,109), nên chưa được
  khẳng định rõ ở mức end-to-end. Chi phí là thêm khoảng 1,2 giây truy xuất trên CPU, thêm torch vào
  image của service và một mô hình 2,3 GB. Nó cũng trả lời một câu ngoài phạm vi (OOS-003, vay mua ô
  tô), và ngưỡng tốt nhất của nó gần bằng 0 nên gần như không có tác dụng chặn. Nên xem xét lại khi
  `bge-m3` đã chạy thật và có câu hỏi thật của học viên.
- **Reranker trên `nomic-embed-text`:** 73%, thấp hơn `bge-m3` đơn thuần mà thời gian truy xuất gấp
  khoảng 70 lần.
- **Các mô hình chuyên tiếng Việt:** `AITeamVN/Vietnamese_Embedding` (77%) và `Vietnamese_Reranker`
  không vượt được các mô hình BGE đa ngôn ngữ ở đây, và mô hình embedding này không có gói Ollama
  chính thức.
- **BM25 hybrid:** không tốt hơn hiện tại (+2 / -4).
- **Top-5:** +4 / -0 (p = 0,125) với chi phí thêm khoảng 760 ms. Kết quả này khớp với phát hiện trước đó.
- **Chỉ hạ ngưỡng xuống 0,50:** +2 / -0, trong khi tỷ lệ từ chối câu ngoài phạm vi giảm từ 38% xuống 6%.

## Tất cả cấu hình, chỉ đo truy xuất

Nguồn: `retrieval_only_focus.json`, và `bge_thresholds_focus.json` cho các ngưỡng cố định của
`bge-m3`. Gồm 95 câu trả lời được (76 câu diễn đạt khác tài liệu) và 16 câu ngoài phạm vi, trong
trường hợp đang mở khóa học. Độ trễ reranker đo trên CPU. Ý nghĩa các cột có trong
[README](../README.md#các-chỉ-số).

| Cấu hình | Trúng (KTC 95%) | Trúng, câu diễn đạt khác | Hit@k không tính ngưỡng | MRR@10 | Từ chối, câu trả lời được | Từ chối, câu ngoài phạm vi | Được / mất so với `current` | p | Truy xuất p50 (ms) | Ngưỡng |
|---|---|---|---|---|---|---|---|---|---|---|
| `current` | 53% (43–62) | 53% | 55% | 0,54 | 9% | 38% | – | – | 18 | 0,550 |
| `current_k5` | 57% (47–66) | 57% | 65% | 0,54 | 9% | 38% | +4 / -0 | 0,125 | 17 | 0,550 |
| `current_t50` | 55% (45–64) | 53% | 55% | 0,54 | 2% | 6% | +2 / -0 | 0,500 | 18 | 0,500 |
| `hybrid_bm25` | 51% (41–60) | 49% | 57% | 0,56 | 9% | 38% | +2 / -4 | 0,688 | 18 | 0,550 |
| `rerank_bge` | 73% (63–81) | 68% | 73% | 0,69 | 1% | 31% | +19 / -0 | <0,001 | 1.720 | 0,001 |
| `rerank_bge_pool15` | 68% (59–77) | 63% | 68% | 0,66 | 0% | 25% | +15 / -0 | <0,001 | 811 | 0,000; 0,001 |
| `rerank_vi` | 65% (55–74) | 62% | 65% | 0,64 | 0% | 31% | +16 / -4 | 0,012 | 1.678 | 0,000 |
| `rerank_small` | 64% (54–73) | 58% | 64% | 0,60 | 0% | 38% | +16 / -5 | 0,027 | 234 | 0,002; 0,003 |
| `embed_bge` (tinh chỉnh) | 79% (70–86) | 82% | 82% | 0,78 | 7% | 31% | +27 / -2 | <0,001 | 25 | 0,339; 0,372 |
| `embed_bge_t30` | 81% (72–88) | 82% | 82% | 0,78 | 4% | 6% | +29 / -2 | <0,001 | 25 | 0,300 |
| `embed_bge_t35` | 79% (70–86) | 82% | 82% | 0,78 | 7% | 25% | +27 / -2 | <0,001 | 24 | 0,350 |
| **`embed_bge_t40`** | **77% (67–84)** | **79%** | **82%** | **0,78** | **7%** | **62%** | **+25 / -2** | **<0,001** | **25** | **0,400** |
| `embed_bge_t45` | 67% (57–76) | 67% | 82% | 0,78 | 22% | 69% | +19 / -5 | 0,007 | 25 | 0,450 |
| `embed_bge_rerank` | 85% (77–91) | 86% | 85% | 0,82 | 1% | 31% | +31 / -0 | <0,001 | 2.170 | 0,001; 0,002 |
| `embed_bge_rerank_pool15` | 85% (77–91) | 86% | 85% | 0,82 | 1% | 25% | +31 / -0 | <0,001 | 1.243 | 0,000; 0,001 |
| `embed_vi` | 77% (67–84) | 75% | 78% | 0,74 | 4% | 25% | +25 / -2 | <0,001 | 57 | 0,181; 0,218 |
| `embed_vi_rerank` | 77% (67–84) | 76% | 77% | 0,77 | 1% | 38% | +29 / -6 | <0,001 | 2.132 | 0,000 |

Mỗi kết quả truy xuất đều giống nhau ở mọi lần chạy lặp lại. Khi không mở khóa học nào
(`retrieval_only_no_focus.json`), bốn cấu hình được đo cùng `current` có tỷ lệ trúng không đổi, với
mô hình chat được giả lập.

## End-to-end, dùng `qwen3:4b`

Nguồn: `llm_focus.json`. Mỗi cấu hình chạy một lượt qua 111 câu hỏi.

| Cấu hình | Từ chối, câu trả lời được | Trả lời có trích dẫn chunk mong đợi | Trả lời câu ngoài phạm vi | Lỗi | p50 (ms) | p90 (ms) |
|---|---|---|---|---|---|---|
| `current` | 23% | 53% | 0/16 | 0 | 1.626 | 2.557 |
| `current_k5` | 21% | 56% | 0/16 | 0 | 2.386 | 3.580 |
| **`embed_bge_t40`** | **12%** | **77%** | **0/16** | **0** | **2.135** | **3.139** |
| `embed_bge` (tinh chỉnh) | 12% | 79% | 0/16 | 0 | 1.947 | 3.043 |
| `rerank_bge` | 6% | 72% | 1/16 | 0 | 3.583 | 4.517 |
| `embed_bge_rerank_pool15` | 3% | 83% | 1/16 | 0 | 3.081 | 4.284 |

Với `current` và `bge-m3`, mô hình từ chối mọi câu ngoài phạm vi lọt tới nó, nên ngưỡng chủ yếu giúp
tiết kiệm thời gian của mô hình. Nhận định này chỉ dựa trên 16 câu.

**Vì sao chọn 0,40.** Ngưỡng này và ngưỡng tinh chỉnh (khoảng 0,34–0,37) chỉ chênh nhau 2 câu và có
tỷ lệ từ chối end-to-end như nhau, nhưng 0,40 chặn được số câu ngoài phạm vi trước khi tới mô hình
nhiều gấp khoảng hai lần. Đến 0,45 thì tỷ lệ trúng giảm mạnh.

## Việc cần làm khi chuyển đổi

Chỉ đổi `OLLAMA_EMBEDDING_MODEL` là chưa đủ:

1. **Prefix.** `chat.py`, `quiz.py`, `documents.py` và `infrastructure.py` hard-code `search_query: `
   và `search_document: ` của nomic. Giữ nguyên các prefix này làm mất 2 câu (77% so với 79%,
   `embed_bge_nomic_prefix`) và làm dịch ngưỡng tốt nhất, nên cần cho phép cấu hình prefix.
2. **Index lại.** Kích thước vector đổi từ 768 lên 1024 và `COLLECTION` đang bị hard-code, nên không
   dùng lại được collection hiện tại. Cần tạo collection mới rồi chuyển sang. Việc embedding 169 chunk
   mất 5,6 giây.
3. **Truy xuất của quiz.** `quiz.py:95` so điểm vector thô với cùng `RETRIEVAL_SCORE_THRESHOLD`. Đã đo
   sau đó trên 48 chương đóng vai bài học (`quiz_thresholds.json`): ngưỡng không loại chunk nào, vì
   điểm thấp nhất là 0,734 với nomic và 0,572 với `bge-m3`. Vì vậy không cần tách ngưỡng riêng.
   Phép đo này không chấm chất lượng câu hỏi quiz.
4. **Triển khai.** Pull `bge-m3` về mọi nơi Ollama chạy trên production.

## Vẫn còn lỗi với `bge-m3`

- **Câu hỏi gõ không dấu: 0/6 ở ngưỡng 0,40**, và `current` cũng 0/6. `bge-m3` xếp 3 câu trong số này
  ở hạng nhất, nhưng điểm cao nhất (0,29–0,32) thấp hơn ngưỡng. Bỏ qua dấu trong hệ số từ vựng chỉ
  có ích ở ngưỡng thấp hơn: 3/6 ở 0,35 và 0/6 ở 0,40 (`bge_thresholds_focus.json`). Reranker đạt 3/6
  trên `bge-m3` và 5/6 trên `nomic-embed-text`. Vấn đề này cần cách sửa riêng, và 6 câu là quá ít để
  chọn cách sửa.
- **Câu diễn đạt khác tài liệu: trượt 16/76**, ví dụ "Làm sao để một giao dịch không bị ghi sổ hai
  lần?" (hạng 14). Vẫn còn 11 trong 95 câu trả lời được bị trả lời từ chunk sai.

## Giới hạn của đánh giá này

- **Câu hỏi không đến từ học viên.** Cơ sở dữ liệu chỉ có một câu không phải của `admin`, và câu đó
  không có chunk mong đợi cụ thể. Một người viết và gán nhãn cả 111 câu sau khi đọc tài liệu, trước
  khi chạy bất kỳ mô hình nào, và không sửa câu nào sau đó. Tỷ lệ tuyệt đối có thể không đúng với
  thực tế. Chênh lệch giữa các cấu hình trên cùng bộ câu hỏi đáng tin hơn.
- **Tập tài liệu và bộ câu hỏi nhỏ.** Chỉ có 2 tài liệu, 169 chunk và 16 câu ngoài phạm vi. Khoảng tin
  cậy rộng khoảng 20 điểm phần trăm, và giá trị p chưa được hiệu chỉnh cho việc so sánh 13 cấu hình.
- **Ngưỡng 0,40 được chọn trên chính dữ liệu này.** Các dòng "tinh chỉnh" mới là ước lượng ngoài mẫu:
  trúng 79% và từ chối 31% câu ngoài phạm vi. Con số 62% của ngưỡng 0,40 nhiều khả năng là lạc quan.
- **Câu trả lời chưa được chấm đúng sai.** "Có trích dẫn chunk mong đợi" cho thấy đã dùng đúng nguồn,
  không có nghĩa câu trả lời đúng. Nhãn cũng có thể bỏ sót chunk trả lời được câu hỏi.
- **Độ trễ chỉ đo một lượt trên máy Mac phát triển**, trong lúc có các phiên khác đang hoạt động. Mô
  hình chat và embedding chạy trên Ollama của máy chủ. Reranker chạy trên CPU của máy chủ, không phải
  trong container, và phần cứng production có thể khác. Chỉ nên xem các chênh lệch là gần đúng.
- **Baseline có chứa code chưa commit.** Nó gồm `drop_navigation_results` của một phiên khác, được
  `retrieval_eval.py` import, nên thay đổi đó phải được commit cùng lúc hoặc trước.
