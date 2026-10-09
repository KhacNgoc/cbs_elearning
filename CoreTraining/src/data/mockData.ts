import type { Course, Question, LeaderboardUser } from '../types';

export const mockCourses: Course[] = [
  {
    id: 'TIEN_VAY',
    name: 'TIẾN VAY',
    iconName: 'Coins',
    lessons: [
      {
        id: 'LN01',
        title: 'LN01. Tổng quan phân hệ cho vay (Loan)',
        courseId: 'TIEN_VAY',
        hint: 'Menu: WebCSR -> Cho vay -> Truy vấn khoản vay',
        documents: [],
        videoUrl: 'https://assets.mixkit.co/videos/preview/mixkit-corporate-workers-discussing-a-project-41566-large.mp4',
        procedureSteps: [
          {
            title: 'Bước 1: Cán bộ QTTD khởi tạo hồ sơ (Maker)',
            steps: [
              'Nhập mã CIF khách hàng để truy vấn thông tin.',
              'Chọn sản phẩm vay tương ứng (Vay tiêu dùng, Vay sản xuất kinh doanh, ...).',
              'Nhập số tiền vay, kỳ hạn, lãi suất và lịch giải ngân dự kiến.',
              'Đính kèm hồ sơ tài sản bảo đảm và nhấn Trình duyệt.'
            ]
          },
          {
            title: 'Bước 2: Kiểm soát viên thẩm định (Checker)',
            steps: [
              'Vào hàng chờ phê duyệt hồ sơ khoản vay.',
              'Đối chiếu thông tin Core Banking với hồ sơ giấy tờ gốc.',
              'Kiểm tra định giá tài sản bảo đảm và các điều kiện ràng buộc.',
              'Nhấn Đồng ý và chuyển tiếp lên cấp thẩm quyền phê duyệt.'
            ]
          }
        ],
        comments: [
          {
            id: 1,
            user: 'nguyenvana',
            avatar: 'A',
            rating: 5,
            content: 'Bài học rất chi tiết và dễ hiểu đối với nhân sự mới như mình!',
            date: '2026-07-10'
          }
        ]
      },
      {
        id: 'LN02',
        title: 'LN02. Quy trình giải ngân khoản vay thông thường',
        courseId: 'TIEN_VAY',
        hint: 'Menu: WebCSR -> Cho vay -> Giao dịch giải ngân',
        documents: [],
        videoUrl: 'https://assets.mixkit.co/videos/preview/mixkit-executives-working-on-a-business-plan-41559-large.mp4',
        procedureSteps: [
          {
            title: 'Quy trình thực hiện tại quầy',
            steps: [
              'Kiểm tra chứng từ đề nghị giải ngân của khách hàng.',
              'Thực hiện giao dịch giải ngân trên phân hệ LN.',
              'In phiếu chi/phiếu chuyển khoản và trình Kiểm soát viên ký duyệt.',
              'Giao tiền mặt hoặc chuyển khoản cho khách hàng.'
            ]
          }
        ],
        comments: []
      }
    ]
  },
  {
    id: 'CHUYEN_TIEN',
    name: 'CHUYỂN TIỀN',
    iconName: 'ArrowLeftRight',
    lessons: [
      {
        id: 'TR01',
        title: 'TR01. Quy trình chuyển tiền nhanh NAPAS 247',
        courseId: 'CHUYEN_TIEN',
        hint: 'Menu: WebCSR -> Chuyển tiền -> Chuyển tiền nhanh Napas 247',
        documents: [],
        videoUrl: 'https://assets.mixkit.co/videos/preview/mixkit-hands-of-a-businessman-working-on-a-laptop-42289-large.mp4',
        procedureSteps: [
          {
            title: 'Quy trình giao dịch viên (Maker)',
            steps: [
              'Nhập số tài khoản/số thẻ thụ hưởng và chọn ngân hàng đích.',
              'Hệ thống tự động truy vấn và hiển thị tên người thụ hưởng để đối chiếu.',
              'Nhập số tiền và nội dung chuyển tiền.',
              'Chọn phương thức thu phí (Người chuyển trả hoặc Người nhận trả) và xác nhận giao dịch.'
            ]
          }
        ],
        comments: []
      }
    ]
  },
  {
    id: 'TIEN_GUI',
    name: 'TIỀN GỬI',
    iconName: 'PiggyBank',
    lessons: [
      {
        id: 'DEP01',
        title: 'DEP01. Quy trình mở sổ tiết kiệm tại quầy',
        courseId: 'TIEN_GUI',
        hint: 'Menu: WebCSR -> Tiền gửi -> Mở tài khoản tiết kiệm có kỳ hạn',
        documents: [],
        videoUrl: 'https://assets.mixkit.co/videos/preview/mixkit-business-people-meeting-around-a-table-41558-large.mp4',
        procedureSteps: [
          {
            title: 'Quy trình mở sổ (Giao dịch viên)',
            steps: [
              'Nhận yêu cầu và kiểm tra giấy tờ tùy thân của khách hàng (CCCD/Hộ chiếu còn hạn).',
              'Quét và cập nhật thông tin khách hàng (nếu chưa có CIF) hoặc truy vấn CIF.',
              'Nhập các tham số sổ tiết kiệm: Số tiền, Kỳ hạn, Phương thức nhận lãi, Chỉ thị tái tục.',
              'In thẻ tiết kiệm từ phôi trắng, đóng dấu và trình Kiểm soát viên duyệt.'
            ]
          }
        ],
        comments: []
      }
    ]
  },
  {
    id: 'CIF',
    name: 'CIF',
    iconName: 'Users',
    lessons: [
      {
        id: 'CIF03',
        title: 'CIF03. Quy trình tạo CIF Khách hàng tổ chức',
        courseId: 'CIF',
        hint: 'Menu: WebCSR -> Tìm kiếm khách hàng -> Thông tin khách hàng / Customer Information -> Tab Tín dụng/Tài chính (Credit/Financial) -> Tab phụ Hạn mức tín dụng khách hàng / Customer Limit',
        documents: [],
        videoUrl: 'https://assets.mixkit.co/videos/preview/mixkit-business-woman-using-a-tablet-at-the-office-41571-large.mp4',
        procedureSteps: [
          {
            title: 'PHẦN 1: CÁN BỘ QTTD THỰC HIỆN (MAKER)',
            steps: [
              '1. ĐĂNG NHẬP & TÌM KIẾM KHÁCH HÀNG: Từ trang chủ WebCSR, sau khi đã tìm kiếm thành công khách hàng, thông tin khách hàng hiển thị tại thanh công cụ phía trên.',
              '2. TRUY CẬP THÔNG TIN KHÁCH HÀNG / CUSTOMER INFORMATION: Người dùng chọn đường dẫn Thông tin khách hàng / Customer Information (hoặc truy cập trực tiếp từ menu Dịch vụ Khách hàng / Customer Services -> Thông tin khách hàng / Customer Information).',
              '3. KHỞI TẠO VÀ NHẬP THÔNG TIN: Nhấn nút Tạo mới (Create). Điền thông tin pháp lý doanh nghiệp, người đại diện và tải lên tệp đính kèm hồ sơ doanh nghiệp.',
              '4. GHI NHẬN & TRÌNH DUYỆT: Nhấn Lưu tạm để kiểm tra lỗi kiểm soát (Lỗi trùng mã số thuế, trùng tên...). Sau khi không còn cảnh báo hệ thống, nhấn Gửi duyệt để đẩy lên kiểm soát viên.'
            ]
          },
          {
            title: 'PHẦN 2: KIỂM SOÁT VIÊN DUYỆT (CHECKER)',
            steps: [
              '1. TRUY VẤN HỒ SƠ CHỜ DUYỆT: Vào màn hình phê duyệt CIF, tìm kiếm theo mã hồ sơ hoặc tên doanh nghiệp.',
              '2. ĐỐI CHIẾU HỒ SƠ GIẤY: Mở tệp đính kèm trên Core Banking và so sánh với hồ sơ bản cứng bao gồm Giấy phép ĐKKD, Điều lệ công ty.',
              '3. PHÊ DUYỆT: Nếu thông tin khớp đúng, chọn Phê duyệt (Approve) để tạo mã CIF chính thức. Nếu sai sót, ghi rõ lý do và Từ chối (Reject) gửi trả về Maker.'
            ]
          }
        ],
        comments: [
          {
            id: 2,
            user: 'nguyenvanb',
            avatar: 'B',
            rating: 4,
            content: 'Bài viết rất hữu ích, quy trình Maker/Checker viết rõ ràng.',
            date: '2026-07-15'
          },
          {
            id: 3,
            user: 'lethic',
            avatar: 'C',
            rating: 5,
            content: 'Video trực quan, xem xong làm được ngay!',
            date: '2026-07-17'
          }
        ]
      },
      {
        id: 'CIF01',
        title: 'CIF01. Quy trình tạo CIF Khách hàng cá nhân',
        courseId: 'CIF',
        hint: 'Menu: WebCSR -> Tìm kiếm khách hàng -> Thông tin khách hàng cá nhân',
        documents: [],
        videoUrl: 'https://assets.mixkit.co/videos/preview/mixkit-startup-team-working-on-laptops-in-office-41553-large.mp4',
        procedureSteps: [
          {
            title: 'Các bước thực hiện (Giao dịch viên)',
            steps: [
              'Kiểm tra giấy tờ tùy thân của khách hàng trên hệ thống để xem đã có CIF chưa.',
              'Nếu chưa có, quét mã QR CCCD để điền nhanh thông tin họ tên, ngày sinh, giới tính, số CCCD.',
              'Nhập số điện thoại di động chính chủ (đã đối soát cơ sở dữ liệu quốc gia) và địa chỉ thường trú.',
              'Chụp ảnh chân dung khách hàng tại quầy và thực hiện đối chiếu KYC.',
              'Nhấn Hoàn thành để hệ thống cấp mã CIF.'
            ]
          }
        ],
        comments: []
      }
    ]
  },
  {
    id: 'TELLER',
    name: 'TELLER',
    iconName: 'Wallet',
    lessons: [
      {
        id: 'TEL01',
        title: 'TEL01. Quy trình quản lý quỹ đầu ngày và cuối ngày',
        courseId: 'TELLER',
        hint: 'Menu: WebCSR -> Teller -> Quản lý két tiền mặt',
        documents: [],
        videoUrl: 'https://assets.mixkit.co/videos/preview/mixkit-business-team-in-a-meeting-working-41563-large.mp4',
        procedureSteps: [
          {
            title: 'Quy trình cuối ngày (Thực hiện bởi Giao dịch viên)',
            steps: [
              'Chạy báo cáo liệt kê tất cả giao dịch thu chi tiền mặt trong ngày.',
              'Thực hiện kiểm đếm tiền mặt thực tế theo từng mệnh giá.',
              'Nhập số liệu kiểm đếm vào giao dịch đóng số dư cuối ngày.',
              'In nhật ký giao dịch và biên bản kiểm kê, ký tên xác nhận và niêm phong két.'
            ]
          }
        ],
        comments: []
      }
    ]
  },
  {
    id: 'GL',
    name: 'GL',
    iconName: 'BookOpen',
    lessons: [
      {
        id: 'GL01',
        title: 'GL01. Quy trình hạch toán bút toán thủ công',
        courseId: 'GL',
        hint: 'Menu: WebCSR -> Sổ cái (GL) -> Tạo bút toán thủ công',
        documents: [],
        videoUrl: 'https://assets.mixkit.co/videos/preview/mixkit-group-of-businesspeople-discussing-charts-41565-large.mp4',
        procedureSteps: [
          {
            title: 'Quy trình tạo bút toán điều chỉnh (Maker)',
            steps: [
              'Xác định nguyên nhân sai sót và tài khoản GL cần điều chỉnh.',
              'Vào phân hệ GL, chọn loại bút toán điều chỉnh.',
              'Nhập tài khoản Nợ, tài khoản Có và số tiền tương ứng.',
              'Nhập nội dung diễn giải chi tiết nguyên nhân điều chỉnh và trình duyệt.'
            ]
          }
        ],
        comments: []
      }
    ]
  },
  {
    id: 'THE',
    name: 'THẺ',
    iconName: 'CreditCard',
    lessons: [
      {
        id: 'CARD01',
        title: 'CARD01. Quy trình phát hành thẻ ghi nợ nội địa',
        courseId: 'THE',
        hint: 'Menu: WebCSR -> Thẻ -> Phát hành thẻ ghi nợ',
        documents: [],
        videoUrl: 'https://assets.mixkit.co/videos/preview/mixkit-colleagues-discussing-data-on-a-monitor-41567-large.mp4',
        procedureSteps: [
          {
            title: 'Quy trình đăng ký phát hành thẻ tại quầy',
            steps: [
              'Nhận biểu mẫu đăng ký phát hành thẻ của khách hàng.',
              'Đối chiếu chữ ký trên biểu mẫu với chữ ký mẫu đã đăng ký trên Core Banking.',
              'Nhập mã CIF, chọn loại thẻ phát hành và liên kết tài khoản thanh toán mặc định.',
              'Xác nhận thông tin địa chỉ nhận thẻ (tại chi nhánh hoặc chuyển phát nhanh về nhà) và hoàn thành giao dịch.'
            ]
          }
        ],
        comments: []
      }
    ]
  }
];

export const mockQuestions: Question[] = [
  // CIF Questions
  {
    id: 1,
    courseId: 'CIF',
    text: 'Mã phân hệ nào dùng để Quản lý Khách hàng trong Core Banking?',
    options: ['CIF', 'DEP', 'LN', 'GL'],
    correctAnswer: 0
  },
  {
    id: 2,
    courseId: 'CIF',
    text: 'Thông tin nào sau đây là BẮT BUỘC khi khởi tạo CIF cho Khách hàng doanh nghiệp?',
    options: ['Số điện thoại nhân viên', 'Mã số thuế doanh nghiệp', 'Mã chứng khoán niêm yết', 'Danh sách cổ đông liên kết'],
    correctAnswer: 1
  },
  {
    id: 3,
    courseId: 'CIF',
    text: 'Để tránh trùng lặp dữ liệu khách hàng cá nhân (trùng CIF), giao dịch viên cần làm gì trước khi tạo mới?',
    options: ['Tìm kiếm kỹ theo Số giấy tờ tùy thân', 'Tìm kiếm theo Tên không dấu', 'Tạo ngẫu nhiên một mã CIF mới', 'Liên hệ chi nhánh khác hỏi thông tin'],
    correctAnswer: 0
  },
  {
    id: 4,
    courseId: 'CIF',
    text: 'Trong quy trình tạo CIF Khách hàng tổ chức, bước phê duyệt cuối cùng do vai trò nào thực hiện?',
    options: ['Maker (Giao dịch viên)', 'Checker (Kiểm soát viên)', 'Thủ quỹ', 'Khách hàng'],
    correctAnswer: 1
  },
  {
    id: 5,
    courseId: 'CIF',
    text: 'Sau khi quét mã QR trên CCCD gắn chip của khách hàng cá nhân, hệ thống Core Banking sẽ làm gì?',
    options: ['Tự động cấp tín dụng cho khách hàng', 'Tự động gửi tin nhắn SMS chúc mừng', 'Tự động trích xuất và điền các thông tin cơ bản của khách hàng', 'Tự động phong tỏa tài khoản thanh toán'],
    correctAnswer: 2
  },

  // TIEN_VAY Questions
  {
    id: 6,
    courseId: 'TIEN_VAY',
    text: 'Ký hiệu phân hệ cho vay trong Core Banking của BIDV là gì?',
    options: ['DEP', 'LN', 'GL', 'TR'],
    correctAnswer: 1
  },
  {
    id: 7,
    courseId: 'TIEN_VAY',
    text: 'Quy trình vòng đời cơ bản của một khoản vay trên Core Banking diễn ra như thế nào?',
    options: [
      'Giải ngân -> Tạo hồ sơ -> Tất toán -> Phê duyệt',
      'Tạo hồ sơ -> Phê duyệt -> Giải ngân -> Thu nợ/lãi -> Tất toán',
      'Phê duyệt -> Tạo hồ sơ -> Giải ngân -> Tất toán',
      'Giải ngân -> Thu nợ/lãi -> Tạo hồ sơ -> Tất toán'
    ],
    correctAnswer: 1
  },
  {
    id: 8,
    courseId: 'TIEN_VAY',
    text: 'Bút toán hạch toán kế toán khi giải ngân khoản vay vào tài khoản thanh toán của khách hàng là:',
    options: [
      'Nợ TK Tiền gửi / Có TK Cho vay',
      'Nợ TK Sổ cái / Có TK Thu nhập',
      'Nợ TK Cho vay (LN) / Có TK Tiền gửi thanh toán (DEP)',
      'Nợ TK Tiền mặt / Có TK Cho vay'
    ],
    correctAnswer: 2
  },
  {
    id: 9,
    courseId: 'TIEN_VAY',
    text: 'Ai là người chịu trách nhiệm nhập liệu thông tin khoản vay ban đầu trên hệ thống?',
    options: ['Checker', 'Maker (Cán bộ QTTD)', 'Thủ quỹ chi nhánh', 'Giám đốc Chi nhánh'],
    correctAnswer: 1
  },
  {
    id: 10,
    courseId: 'TIEN_VAY',
    text: 'Điều kiện nào sau đây KHÔNG bắt buộc khi thực hiện giải ngân khoản vay thông thường?',
    options: [
      'Khoản vay đã được phê duyệt hợp lệ',
      'Tài sản bảo đảm đã được phong tỏa trên hệ thống',
      'Khách hàng phải có tài khoản tiết kiệm tại ngân hàng',
      'Hợp đồng thế chấp đã ký kết đúng quy định'
    ],
    correctAnswer: 2
  },

  // CHUYEN_TIEN Questions
  {
    id: 11,
    courseId: 'CHUYEN_TIEN',
    text: 'Hạn mức chuyển tiền nhanh NAPAS 247 tối đa cho một giao dịch tại quầy hiện nay là bao nhiêu?',
    options: ['100 triệu VND', '300 triệu VND', '499.999.999 VND', '1 tỷ VND'],
    correctAnswer: 2
  },
  {
    id: 12,
    courseId: 'CHUYEN_TIEN',
    text: 'Khi giao dịch viên nhập số tài khoản thụ hưởng trên màn hình NAPAS 247, hệ thống Core Banking sẽ thực hiện điều gì?',
    options: [
      'Tự động trừ tiền ngay lập tức',
      'Tự động truy vấn hiển thị tên chủ tài khoản thụ hưởng tại ngân hàng đích',
      'Tự động gửi mã OTP về số điện thoại khách hàng',
      'Yêu cầu tải lên hồ sơ chứng từ đính kèm'
    ],
    correctAnswer: 1
  },
  {
    id: 13,
    courseId: 'CHUYEN_TIEN',
    text: 'Lỗi giao dịch trừ tiền tài khoản người chuyển nhưng tài khoản thụ hưởng chưa nhận được tiền thường được giải quyết qua quy trình nào?',
    options: ['Tra soát và đối soát định kỳ với Napas', 'Tự động hoàn tiền sau 5 phút', 'Yêu cầu khách hàng tự liên hệ ngân hàng nhận', 'Hạch toán bút toán thủ công ngay'],
    correctAnswer: 0
  },
  {
    id: 14,
    courseId: 'CHUYEN_TIEN',
    text: 'Phương thức thu phí chuyển tiền nào có thể được lựa chọn khi thực hiện giao dịch tại quầy?',
    options: [
      'Chỉ người chuyển trả phí',
      'Chỉ người nhận trả phí',
      'Người chuyển trả phí HOẶC Người nhận trả phí',
      'Ngân hàng chịu toàn bộ chi phí'
    ],
    correctAnswer: 2
  },
  {
    id: 15,
    courseId: 'CHUYEN_TIEN',
    text: 'Phân hệ nào xử lý luồng giao dịch chuyển tiền liên ngân hàng đi và đến?',
    options: ['GL', 'LN', 'TR (Transfer)', 'DEP'],
    correctAnswer: 2
  },

  // TIEN_GUI Questions
  {
    id: 16,
    courseId: 'TIEN_GUI',
    text: 'Sổ tiết kiệm trả lãi cuối kỳ có đặc điểm gì về lãi suất?',
    options: [
      'Lãi suất thay đổi hàng tháng',
      'Lãi suất cố định suốt kỳ hạn và được nhận cùng gốc khi tất toán',
      'Được nhận lãi trước ngay khi gửi tiền',
      'Không được hưởng lãi nếu rút trước hạn 1 ngày'
    ],
    correctAnswer: 1
  },
  {
    id: 17,
    courseId: 'TIEN_GUI',
    text: 'Chứng từ nào là bắt buộc khi khách hàng cá nhân đến quầy yêu cầu mở sổ tiết kiệm?',
    options: ['Giấy tờ tùy thân hợp lệ (CCCD/Hộ chiếu)', 'Giấy xác nhận bảng lương', 'Giấy đăng ký tạm trú', 'Hóa đơn tiền điện nước'],
    correctAnswer: 0
  },
  {
    id: 18,
    courseId: 'TIEN_GUI',
    text: 'Khi thực hiện in Thẻ tiết kiệm bàn giao cho khách hàng, phôi sổ tiết kiệm trắng được quản lý như thế nào?',
    options: [
      'Để tự do tại bàn làm việc của giao dịch viên',
      'Quản lý theo số series và lưu trữ trong kho quỹ bảo mật',
      'In sẵn chữ ký của giám đốc lên phôi',
      'Không cần quản lý số lượng phôi'
    ],
    correctAnswer: 1
  },
  {
    id: 19,
    courseId: 'TIEN_GUI',
    text: 'Chỉ thị tái tục "Gốc quay vòng, lãi nhập gốc" (Rollover) có nghĩa là:',
    options: [
      'Đến hạn toàn bộ gốc và lãi chuyển sang tài khoản thanh toán',
      'Đến hạn tiền lãi tự rút ra, tiền gốc gửi tiếp kỳ hạn mới',
      'Đến hạn toàn bộ gốc và lãi tự động tái tục sang một kỳ hạn mới tương đương',
      'Tự động tăng lãi suất gấp đôi ở kỳ tiếp theo'
    ],
    correctAnswer: 2
  },
  {
    id: 20,
    courseId: 'TIEN_GUI',
    text: 'Phân hệ nào xử lý các giao dịch mở và quản lý sổ tiết kiệm, tài khoản thanh toán?',
    options: ['LN', 'DEP (Deposit)', 'GL', 'TR'],
    correctAnswer: 1
  },

  // TELLER Questions
  {
    id: 21,
    courseId: 'TELLER',
    text: 'Khi phát hiện tồn quỹ thực tế tại két cuối ngày lệch so với số dư trên Core Banking, Teller phải làm gì đầu tiên?',
    options: [
      'Tự bỏ tiền túi bù vào nếu thiếu',
      'Báo cáo ngay cho Kiểm soát viên/Trưởng phòng kế toán để kiểm tra chéo giao dịch',
      'Hạch toán điều chỉnh bút toán thủ công',
      'Ra về bình thường và xử lý vào hôm sau'
    ],
    correctAnswer: 1
  },
  {
    id: 22,
    courseId: 'TELLER',
    text: 'Quy trình bàn giao quỹ đầu ngày giữa Thủ quỹ chi nhánh và Giao dịch viên yêu cầu phải:',
    options: [
      'Ký biên bản khống trước khi kiểm tiền',
      'Kiểm đếm trực tiếp tiền mặt thực tế và khớp số liệu trên hệ thống trước khi nhận bàn giao',
      'Chuyển khoản ảo qua tài khoản cá nhân',
      'Nhờ người khác nhận hộ'
    ],
    correctAnswer: 1
  },
  {
    id: 23,
    courseId: 'TELLER',
    text: 'Mục đích chính của việc thiết lập hạn mức tồn quỹ két tiền mặt cho từng giao dịch viên là gì?',
    options: [
      'Hạn chế rủi ro thất thoát tiền mặt tại quầy giao dịch',
      'Giảm bớt thời gian kiểm tiền cuối ngày',
      'Tăng doanh số giao dịch của ngân hàng',
      'Bắt buộc Teller phải liên tục đi nộp tiền lẻ'
    ],
    correctAnswer: 0
  },
  {
    id: 24,
    courseId: 'TELLER',
    text: 'Thao tác nào Giao dịch viên phải làm cuối mỗi ngày làm việc?',
    options: [
      'Đăng xuất hệ thống và ra về ngay',
      'Kiểm kê tiền mặt thực tế theo mệnh giá và chạy giao dịch đóng số dư cuối ngày',
      'Tự duyệt các giao dịch còn treo',
      'Chuyển toàn bộ tiền mặt về két của khách hàng'
    ],
    correctAnswer: 1
  },
  {
    id: 25,
    courseId: 'TELLER',
    text: 'Báo cáo nào giúp giao dịch viên đối soát toàn bộ các giao dịch thu chi tiền mặt đã thực hiện trong ngày?',
    options: ['Báo cáo tài chính năm', 'Nhật ký giao dịch tiền mặt của Teller', 'Bảng cân đối kế toán chi nhánh', 'Sơ đồ tổ chức nhân sự'],
    correctAnswer: 1
  },

  // GL Questions
  {
    id: 26,
    courseId: 'GL',
    text: 'Bút toán thủ công (Manual Journal Entry) trong phân hệ GL được sử dụng trong trường hợp nào?',
    options: [
      'Rút tiền mặt tiết kiệm thông thường',
      'Giải ngân khoản vay khách hàng cá nhân',
      'Điều chỉnh sai sót kế toán hoặc ghi nhận giao dịch đặc thù được phê duyệt',
      'Thu phí chuyển tiền NAPAS tự động'
    ],
    correctAnswer: 2
  },
  {
    id: 27,
    courseId: 'GL',
    text: 'Quy tắc cơ bản nhất của kế toán kép trong hạch toán GL là gì?',
    options: [
      'Tổng số tiền phát sinh Nợ phải lớn hơn phát sinh Có',
      'Tổng số tiền phát sinh Nợ phải bằng tổng số tiền phát sinh Có',
      'Chỉ hạch toán vào tài khoản ngoại bảng',
      'Tổng số dư Nợ luôn bằng 0'
    ],
    correctAnswer: 1
  },
  {
    id: 28,
    courseId: 'GL',
    text: 'Tài khoản GL loại 1 (đầu 1) trong hệ thống tài khoản kế toán ngân hàng biểu diễn loại tài sản nào?',
    options: ['Tài sản Có (Tiền mặt, tiền gửi tại NHNN, các khoản cho vay...)', 'Nợ phải trả', 'Vốn chủ sở hữu', 'Doanh nghiệp liên kết'],
    correctAnswer: 0
  },
  {
    id: 29,
    courseId: 'GL',
    text: 'Bút toán hạch toán GL hợp lệ là bút toán:',
    options: [
      'Chỉ có 1 dòng ghi Nợ',
      'Có tổng số tiền phát sinh Nợ bằng phát sinh Có và các tài khoản hạch toán hoạt động hợp lệ',
      'Chưa được kiểm soát viên phê duyệt',
      'Sử dụng tài khoản nội bộ tùy ý'
    ],
    correctAnswer: 1
  },
  {
    id: 30,
    courseId: 'GL',
    text: 'Ai chịu trách nhiệm phê duyệt bút toán GL điều chỉnh sai sót do Maker tạo ra?',
    options: ['Chính Maker đó', 'Kiểm soát viên (Checker) hoặc Trưởng phòng Kế toán tài chính', 'Khách hàng liên quan', 'Thủ quỹ chi nhánh'],
    correctAnswer: 1
  },

  // THE Questions
  {
    id: 31,
    courseId: 'THE',
    text: 'Thẻ ghi nợ nội địa (Debit Card) liên kết trực tiếp với loại tài khoản nào của khách hàng?',
    options: ['Tài khoản tiết kiệm có kỳ hạn', 'Tài khoản tiền gửi thanh toán (không kỳ hạn)', 'Tài khoản thẻ tín dụng', 'Tài khoản vay thấu chi'],
    correctAnswer: 1
  },
  {
    id: 32,
    courseId: 'THE',
    text: 'Khi phát hành thẻ ghi nợ tại quầy, giao dịch viên bắt buộc phải thực hiện bước nào để đối chiếu chữ ký?',
    options: [
      'Yêu cầu khách hàng tự ký khống lên phôi thẻ',
      'Truy vấn chữ ký mẫu của khách hàng trên Core Banking và so sánh với chữ ký trên đơn đăng ký',
      'Bỏ qua bước chữ ký nếu khách hàng có CCCD gắn chip',
      'Yêu cầu khách hàng điểm chỉ vân tay thay thế chữ ký'
    ],
    correctAnswer: 1
  },
  {
    id: 33,
    courseId: 'THE',
    text: 'Dịch vụ phát hành "Thẻ phi vật lý" nghĩa là gì?',
    options: [
      'In thẻ nhựa bằng chất liệu bảo vệ môi trường',
      'Khách hàng đăng ký thẻ trên Smart Banking và sử dụng thẻ ảo trực tuyến mà không cần nhận thẻ nhựa vật lý',
      'Phát hành thẻ cho khách hàng không có CIF',
      'Phát hành thẻ tín dụng không giới hạn hạn mức'
    ],
    correctAnswer: 1
  },
  {
    id: 34,
    courseId: 'THE',
    text: 'Trạng thái thẻ "Active" trên Core Banking biểu thị điều gì?',
    options: [
      'Thẻ đã bị khóa do nhập sai PIN quá 3 lần',
      'Thẻ đã hết hạn sử dụng',
      'Thẻ hoạt động bình thường, sẵn sàng thực hiện giao dịch',
      'Thẻ chưa được giao cho khách hàng'
    ],
    correctAnswer: 2
  },
  {
    id: 35,
    courseId: 'THE',
    text: 'Khi khách hàng báo mất thẻ vật lý, giao dịch viên cần thực hiện giao dịch nào đầu tiên trên phân hệ Thẻ?',
    options: ['Phát hành lại thẻ mới', 'Tất toán tài khoản liên kết', 'Khóa thẻ tạm thời (Block/Suspend thẻ)', 'Thay đổi hạn mức thẻ'],
    correctAnswer: 2
  }
];

export const mockLeaderboard: LeaderboardUser[] = [
  { rank: 1, username: 'nguyenvana', fullName: 'Nguyễn Văn An', points: 2850, avatarColor: '#4F46E5', badgesCount: 6, completedLessonsCount: 8 },
  { rank: 2, username: 'tranb', fullName: 'Trần Thị Bình', points: 2600, avatarColor: '#10B981', badgesCount: 5, completedLessonsCount: 7 },
  { rank: 3, username: 'lephuc', fullName: 'Lê Hoàng Phúc', points: 2450, avatarColor: '#F59E0B', badgesCount: 4, completedLessonsCount: 6 },
  { rank: 4, username: 'phamduc', fullName: 'Phạm Minh Đức', points: 2100, avatarColor: '#EF4444', badgesCount: 3, completedLessonsCount: 5 },
  { rank: 5, username: 'quantrh', fullName: 'Quản Trị Hệ Thống (Bạn)', points: 1850, avatarColor: '#005B41', badgesCount: 3, completedLessonsCount: 4 },
  { rank: 6, username: 'hoangyen', fullName: 'Hoàng Kim Yến', points: 1500, avatarColor: '#EC4899', badgesCount: 2, completedLessonsCount: 3 },
  { rank: 7, username: 'vuongminh', fullName: 'Vương Tuấn Minh', points: 1200, avatarColor: '#8B5CF6', badgesCount: 1, completedLessonsCount: 2 }
];
