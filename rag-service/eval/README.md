# Đánh giá truy xuất

Đo xem chatbot có tìm đúng chunk cho câu hỏi tiếng Việt hay không, kể cả những câu diễn đạt khác,
không dùng lại từ ngữ của tài liệu. Số liệu mới nhất và đề xuất nằm trong
[results/REPORT.md](results/REPORT.md).

## Cách chạy

Qdrant và Ollama phải đang chạy, và các khóa học đã được index.

```bash
python3 -m venv .venv && .venv/bin/pip install -r eval/requirements.txt
```

Tạo các collection riêng. Bước này đọc chunk đang chạy thật và chỉ ghi vào các collection `eval_*`:

```bash
ollama pull bge-m3
```

```bash
.venv/bin/python eval/retrieval_eval.py index --embed ollama:nomic-embed-text
```

```bash
.venv/bin/python eval/retrieval_eval.py index --embed ollama:bge-m3
```

```bash
.venv/bin/python eval/retrieval_eval.py index --embed st:AITeamVN/Vietnamese_Embedding
```

Chỉ đo truy xuất, mất vài phút trên CPU:

```bash
.venv/bin/python eval/retrieval_eval.py run --configs all --out eval/results/retrieval_only_focus.json
```

Dùng mô hình chat thật để đo tỷ lệ từ chối và độ trễ end-to-end. Cách này lâu hơn nhiều:

```bash
.venv/bin/python eval/retrieval_eval.py run --configs current,rerank_bge --llm --out eval/results/llm_focus.json
```

`--scope none` giả lập học viên không mở khóa học nào. `RERANK_DEVICE=mps` chạy các mô hình
sentence-transformers trên GPU của Apple. Mặc định là CPU vì container rag-service không truy cập
được GPU trên macOS.

Xóa các collection đánh giá khi không còn cần. Service không dùng collection nào trong số này:

```bash
curl -X DELETE localhost:6333/collections/eval_ollama_nomic_embed_text
```

```bash
curl -X DELETE localhost:6333/collections/eval_ollama_bge_m3
```

```bash
curl -X DELETE localhost:6333/collections/eval_ollama_bge_m3_nomic_prefix
```

```bash
curl -X DELETE localhost:6333/collections/eval_st_aiteamvn_vietnamese_embedding
```

## Cách hoạt động

Mọi câu hỏi đều đi qua `answer_chat` thật. Phạm vi tìm kiếm, giới hạn ứng viên, hệ số ưu tiên khóa
học đang mở, khử trùng lặp và giới hạn ngữ cảnh đều là code production; mỗi cấu hình chỉ thay cách
tìm và chấm điểm ứng viên. Khi không có `--llm`, mô hình chat được giả lập, và các section đưa vào
prompt được đọc từ chính prompt mà nó nhận.

## Các chỉ số

| Chỉ số | Ý nghĩa |
|---|---|
| Trúng (Hit) | Có chunk mong đợi trong prompt, sau khi áp ngưỡng và top-k |
| Hit@k không tính ngưỡng | Như trên nhưng bỏ qua ngưỡng, chỉ thể hiện chất lượng xếp hạng |
| MRR@10 | Trung bình của 1/hạng của chunk mong đợi đầu tiên, bằng 0 nếu dưới hạng 10 |
| Từ chối, câu trả lời được | Không chunk nào qua ngưỡng ở câu mà tài liệu trả lời được. Càng thấp càng tốt |
| Từ chối, câu ngoài phạm vi | Không chunk nào qua ngưỡng ở câu mà tài liệu không trả lời được. Càng cao càng tốt |
| Được / mất, p | Số câu được và mất so với `current`, kèm kiểm định McNemar chính xác |
| Truy xuất p50 | Trung vị của embedding + tìm kiếm + chấm điểm, không tính lần nạp mô hình đầu tiên |
| End-to-end p50 (`--llm`) | Truy xuất + `answer_chat` với mô hình thật |

Có thể cải thiện riêng tỷ lệ từ chối bằng cách chấp nhận mọi thứ, nên luôn phải đọc tỷ lệ từ chối
của câu trả lời được và câu ngoài phạm vi cùng nhau.

**Ngưỡng.** Ngưỡng không dùng chung được giữa các cách chấm điểm, nên mỗi cấu hình mới tự tinh chỉnh
ngưỡng của mình, sao cho đạt tỷ lệ từ chối câu ngoài phạm vi như production ở 0,55. Nhờ vậy, tỷ lệ
trúng được so sánh ở mức rủi ro xấp xỉ nhau về việc trả lời những gì tài liệu không đề cập. Câu hỏi
được chia thành hai phần theo hash của id, và mỗi câu dùng ngưỡng chọn từ phần còn lại, nên bảng có
thể hiện hai ngưỡng. `current` dùng đúng cấu hình service đang chạy.

## Bộ câu hỏi

[questions.jsonl](questions.jsonl) có một câu hỏi trên mỗi dòng:

| Trường | Ý nghĩa |
|---|---|
| `kind` | `paraphrase` (diễn đạt khác), `direct` (trực tiếp), `no_diacritics` (không dấu) hoặc `out_of_scope` (ngoài phạm vi) |
| `course` | Khóa học mà học viên giả lập đang mở |
| `expected` | Các section trả lời được câu hỏi; chỉ cần một section là tính trúng. Để trống với câu ngoài phạm vi |

Một nhãn như `CIF-20 › Câu 5` khớp với phần đầu của từng cấp trong đường dẫn section. Lần chạy sẽ
dừng và báo lỗi nếu một nhãn không khớp đúng một section đã index, nên việc index lại không thể âm
thầm làm hỏng bộ câu hỏi.

**Giới hạn: câu hỏi không đến từ học viên.** `rag_conversation_messages` chỉ có một câu không phải của
`admin`, là `nội dung chính của khoá học?`. Câu này đi theo luồng tóm tắt khóa học và không có chunk
mong đợi cụ thể. Các câu hỏi được viết tay dựa trên tài liệu đã index. Hãy bổ sung câu hỏi thật khi có:

```sql
select distinct m.content
from rag_conversation_messages m
join rag_conversations c on c.id = m.conversation_id
join users u on u.id = c.user_id
where m.role = 'user' and u.username <> 'admin';
```

## Ngưỡng của quiz

Quiz so ngưỡng với điểm vector thô, khác với chat. `eval/quiz_threshold_eval.py` lấy mỗi chương làm
một bài học và đo xem ngưỡng có làm thiếu nguồn hay không.

## Thêm cấu hình

Thêm một `EvalConfig` vào `_configs()` trong `retrieval_eval.py`. Mô hình embedding mới còn cần một
mục trong `EMBEDDERS` và một lần chạy `index`. `current` luôn được đưa vào làm baseline. `nomic` là cấu hình cũ trước khi đổi sang `bge-m3`.
