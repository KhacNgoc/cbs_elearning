package com.bidv.coretraining.config;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
@ConditionalOnProperty(
        prefix = "app.data-initializer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserQuizScoreRepository userQuizScoreRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserGroupRepository userGroupRepository;

    public DataInitializer(UserRepository userRepository, TopicRepository topicRepository,
                           LessonRepository lessonRepository, QuestionRepository questionRepository,
                           BadgeRepository badgeRepository, UserBadgeRepository userBadgeRepository,
                           UserQuizScoreRepository userQuizScoreRepository, CommentRepository commentRepository,
                           PasswordEncoder passwordEncoder, UserGroupRepository userGroupRepository) {
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.userQuizScoreRepository = userQuizScoreRepository;
        this.commentRepository = commentRepository;
        this.passwordEncoder = passwordEncoder;
        this.userGroupRepository = userGroupRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Ensure new topic-based groups exist in the database
        if (!userGroupRepository.existsById("CIF")) {
            // Find topics in database or create them if not present
            Topic cif = topicRepository.findById("CIF").orElseGet(() -> topicRepository.save(Topic.builder().id("CIF").name("CIF").iconName("Users").build()));
            Topic tienVay = topicRepository.findById("TIEN_VAY").orElseGet(() -> topicRepository.save(Topic.builder().id("TIEN_VAY").name("TIỀN VAY").iconName("Coins").build()));
            Topic chuyenTien = topicRepository.findById("CHUYEN_TIEN").orElseGet(() -> topicRepository.save(Topic.builder().id("CHUYEN_TIEN").name("CHUYỂN TIỀN").iconName("ArrowLeftRight").build()));
            Topic tienGui = topicRepository.findById("TIEN_GUI").orElseGet(() -> topicRepository.save(Topic.builder().id("TIEN_GUI").name("TIỀN GỬI").iconName("PiggyBank").build()));
            Topic teller = topicRepository.findById("TELLER").orElseGet(() -> topicRepository.save(Topic.builder().id("TELLER").name("TELLER").iconName("Wallet").build()));
            Topic gl = topicRepository.findById("GL").orElseGet(() -> topicRepository.save(Topic.builder().id("GL").name("GL").iconName("BookOpen").build()));
            Topic the = topicRepository.findById("THE").orElseGet(() -> topicRepository.save(Topic.builder().id("THE").name("THẺ").iconName("CreditCard").build()));

            // Transition users away from old GDV, QHKH, KSV groups
            userRepository.findAll().forEach(u -> {
                if (u.getUserGroup() != null) {
                    String oldGroupId = u.getUserGroup().getId();
                    if ("GDV".equals(oldGroupId) || "QHKH".equals(oldGroupId) || "KSV".equals(oldGroupId)) {
                        u.setUserGroup(null);
                        userRepository.save(u);
                    }
                }
            });

            // Delete old groups
            if (userGroupRepository.existsById("GDV")) userGroupRepository.deleteById("GDV");
            if (userGroupRepository.existsById("QHKH")) userGroupRepository.deleteById("QHKH");
            if (userGroupRepository.existsById("KSV")) userGroupRepository.deleteById("KSV");

            // Seed new groups
            UserGroup cifGroup = userGroupRepository.save(UserGroup.builder().id("CIF").name("Nhóm nghiệp vụ CIF").mandatoryTopics(new HashSet<>(Arrays.asList(cif))).build());
            UserGroup tienVayGroup = userGroupRepository.save(UserGroup.builder().id("TIEN_VAY").name("Nhóm nghiệp vụ Tiền vay").mandatoryTopics(new HashSet<>(Arrays.asList(tienVay))).build());
            UserGroup chuyenTienGroup = userGroupRepository.save(UserGroup.builder().id("CHUYEN_TIEN").name("Nhóm nghiệp vụ Chuyển tiền").mandatoryTopics(new HashSet<>(Arrays.asList(chuyenTien))).build());
            UserGroup tienGuiGroup = userGroupRepository.save(UserGroup.builder().id("TIEN_GUI").name("Nhóm nghiệp vụ Tiền gửi").mandatoryTopics(new HashSet<>(Arrays.asList(tienGui))).build());
            UserGroup tellerGroup = userGroupRepository.save(UserGroup.builder().id("TELLER").name("Nhóm nghiệp vụ Teller").mandatoryTopics(new HashSet<>(Arrays.asList(teller))).build());
            UserGroup glGroup = userGroupRepository.save(UserGroup.builder().id("GL").name("Nhóm nghiệp vụ GL").mandatoryTopics(new HashSet<>(Arrays.asList(gl))).build());
            UserGroup theGroup = userGroupRepository.save(UserGroup.builder().id("THE").name("Nhóm nghiệp vụ Thẻ").mandatoryTopics(new HashSet<>(Arrays.asList(the))).build());

            // Re-assign default users to new groups
            userRepository.findByUsername("system").ifPresent(u -> { u.setUserGroup(glGroup); userRepository.save(u); });
            userRepository.findByUsername("quantrh").ifPresent(u -> { u.setUserGroup(cifGroup); userRepository.save(u); });
            userRepository.findByUsername("nguyenvana").ifPresent(u -> { u.setUserGroup(cifGroup); userRepository.save(u); });
            userRepository.findByUsername("tranb").ifPresent(u -> { u.setUserGroup(tienVayGroup); userRepository.save(u); });
            userRepository.findByUsername("lephuc").ifPresent(u -> { u.setUserGroup(chuyenTienGroup); userRepository.save(u); });
            userRepository.findByUsername("phamduc").ifPresent(u -> { u.setUserGroup(tienGuiGroup); userRepository.save(u); });
            userRepository.findByUsername("hoangyen").ifPresent(u -> { u.setUserGroup(tellerGroup); userRepository.save(u); });
            userRepository.findByUsername("vuongminh").ifPresent(u -> { u.setUserGroup(theGroup); userRepository.save(u); });
        }

        System.out.println("========== ALL USERS IN DB ON STARTUP ==========");
        userRepository.findAll().forEach(u -> {
            System.out.println("User: " + u.getUsername() + ", Role: " + u.getRole() + ", Email: " + u.getEmail());
        });
        System.out.println("================================================");

        System.out.println("========== ALL USER GROUPS IN DB ==========");
        userGroupRepository.findAll().forEach(g -> {
            System.out.println("Group ID: " + g.getId() + ", Name: " + g.getName());
        });
        System.out.println("===========================================");

        if (!userRepository.existsByUsername("system")) {
            UserGroup systemGroup = userGroupRepository.findById("GL").orElse(null);
            userRepository.save(User.builder()
                    .username("system")
                    .password(passwordEncoder.encode("123456"))
                    .fullName("Hệ Thống")
                    .avatarColor("#8B5CF6")
                    .employeeId("SYSTEM")
                    .email("system@bidv.com.vn")
                    .role("ROLE_ADMIN")
                    .points(2000)
                    .userGroup(systemGroup)
                    .mustChangePassword(false)
                    .build());
        } else {
            // Force system user to ADMIN and reset password to 123456 for local testing
            userRepository.findByUsername("system").ifPresent(user -> {
                user.setEmail("system@bidv.com.vn");
                user.setEmployeeId("SYSTEM");
                user.setRole("ROLE_ADMIN");
                user.setPassword(passwordEncoder.encode("123456"));
                user.setMustChangePassword(false);
                if (user.getUserGroup() == null) {
                    UserGroup systemGroup = userGroupRepository.findById("GL").orElse(null);
                    user.setUserGroup(systemGroup);
                }
                userRepository.save(user);
            });
        }

        // Repair other default users' emails in case they exist from old runs
        userRepository.findByUsername("admin").ifPresent(user -> {
            user.setMustChangePassword(false);
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                user.setEmail("admin@bidv.com.vn");
                user.setEmployeeId("ADM001");
            }
            userRepository.save(user);
        });

        userRepository.findByUsername("quantrh").ifPresent(user -> {
            user.setMustChangePassword(false);
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                user.setEmail("quantrh@bidv.com.vn");
                user.setEmployeeId("QTR001");
            }
            userRepository.save(user);
        });

        if (topicRepository.count() > 0) {
            return; // Data already initialized
        }

        // 1. Create Topics
        Topic cif = topicRepository.save(Topic.builder().id("CIF").name("CIF").iconName("Users").build());
        Topic tienVay = topicRepository.save(Topic.builder().id("TIEN_VAY").name("TIỀN VAY").iconName("Coins").build());
        Topic chuyenTien = topicRepository.save(Topic.builder().id("CHUYEN_TIEN").name("CHUYỂN TIỀN").iconName("ArrowLeftRight").build());
        Topic tienGui = topicRepository.save(Topic.builder().id("TIEN_GUI").name("TIỀN GỬI").iconName("PiggyBank").build());
        Topic teller = topicRepository.save(Topic.builder().id("TELLER").name("TELLER").iconName("Wallet").build());
        Topic gl = topicRepository.save(Topic.builder().id("GL").name("GL").iconName("BookOpen").build());
        Topic the = topicRepository.save(Topic.builder().id("THE").name("THẺ").iconName("CreditCard").build());

        // 1b. Create User Groups
        UserGroup cifGroup = userGroupRepository.save(UserGroup.builder().id("CIF").name("Nhóm nghiệp vụ CIF").mandatoryTopics(new HashSet<>(Arrays.asList(cif))).build());
        UserGroup tienVayGroup = userGroupRepository.save(UserGroup.builder().id("TIEN_VAY").name("Nhóm nghiệp vụ Tiền vay").mandatoryTopics(new HashSet<>(Arrays.asList(tienVay))).build());
        UserGroup chuyenTienGroup = userGroupRepository.save(UserGroup.builder().id("CHUYEN_TIEN").name("Nhóm nghiệp vụ Chuyển tiền").mandatoryTopics(new HashSet<>(Arrays.asList(chuyenTien))).build());
        UserGroup tienGuiGroup = userGroupRepository.save(UserGroup.builder().id("TIEN_GUI").name("Nhóm nghiệp vụ Tiền gửi").mandatoryTopics(new HashSet<>(Arrays.asList(tienGui))).build());
        UserGroup tellerGroup = userGroupRepository.save(UserGroup.builder().id("TELLER").name("Nhóm nghiệp vụ Teller").mandatoryTopics(new HashSet<>(Arrays.asList(teller))).build());
        UserGroup glGroup = userGroupRepository.save(UserGroup.builder().id("GL").name("Nhóm nghiệp vụ GL").mandatoryTopics(new HashSet<>(Arrays.asList(gl))).build());
        UserGroup theGroup = userGroupRepository.save(UserGroup.builder().id("THE").name("Nhóm nghiệp vụ Thẻ").mandatoryTopics(new HashSet<>(Arrays.asList(the))).build());

        // 2. Create Lessons for CIF
        Lesson cif01 = Lesson.builder()
                .id("CIF01")
                .title("CIF01. Quy trình tạo CIF Khách hàng cá nhân")
                .topic(cif)
                .hint("Menu: WebCSR -> Tìm kiếm khách hàng -> Thông tin khách hàng cá nhân")
                .videoUrl("https://assets.mixkit.co/videos/preview/mixkit-startup-team-working-on-laptops-in-office-41553-large.mp4")
                .documentSlides(Arrays.asList(
                        "CIF Khách hàng cá nhân (Retail CIF) dùng để quản lý tất cả các sản phẩm của một khách hàng cá nhân.",
                        "Yêu cầu giấy tờ: CCCD còn hạn (quét mã QR để tự động trích xuất thông tin), Hộ chiếu (đối với người nước ngoài).",
                        "Ngăn ngừa trùng lặp dữ liệu: Tìm kiếm kỹ thông tin theo Số giấy tờ tùy thân trước khi tạo mới để tránh trùng CIF."
                ))
                .build();
        cif01.setProcedureSteps(Arrays.asList(
                ProcedureSection.builder()
                        .title("Các bước thực hiện (Giao dịch viên)")
                        .steps(Arrays.asList(
                                "Kiểm tra giấy tờ tùy thân của khách hàng trên hệ thống để xem đã có CIF chưa.",
                                "Nếu chưa có, quét mã QR CCCD để điền nhanh thông tin họ tên, ngày sinh, giới tính, số CCCD.",
                                "Nhập số điện thoại di động chính chủ và địa chỉ thường trú.",
                                "Chụp ảnh chân dung khách hàng tại quầy và thực hiện đối chiếu KYC.",
                                "Nhấn Hoàn thành để hệ thống cấp mã CIF."
                        ))
                        .lesson(cif01)
                        .sectionOrder(0)
                        .build()
        ));
        lessonRepository.save(cif01);

        Lesson cif03 = Lesson.builder()
                .id("CIF03")
                .title("CIF03. Quy trình tạo CIF Khách hàng tổ chức")
                .topic(cif)
                .hint("Menu: WebCSR -> Tìm kiếm khách hàng -> Thông tin khách hàng / Customer Information -> Tab Tín dụng/Tài chính -> Tab Hạn mức tín dụng khách hàng")
                .videoUrl("https://assets.mixkit.co/videos/preview/mixkit-business-woman-using-a-tablet-at-the-office-41571-large.mp4")
                .documentSlides(Arrays.asList(
                        "Khái niệm CIF Khách hàng tổ chức (Corporate CIF). CIF là mã định danh duy nhất của mỗi khách hàng trên toàn hệ thống Core Banking BIDV.",
                        "Điều kiện khởi tạo CIF Doanh nghiệp: Phải có Đăng ký kinh doanh hợp pháp, quyết định bổ nhiệm đại diện pháp luật và danh sách ban điều hành.",
                        "Các trường thông tin bắt buộc: Tên doanh nghiệp (Tiếng Việt và Tiếng Anh), Mã số thuế doanh nghiệp, Địa chỉ trụ sở, Lĩnh vực hoạt động kinh tế (mã ngành kinh tế cấp 4).",
                        "Vai trò của các bên liên quan: Người đại diện theo pháp luật, Kế toán trưởng, Người được ủy quyền giao dịch.",
                        "Quy tắc chống rửa tiền (AML/KYC): Đánh giá rủi ro khách hàng tổ chức dựa trên ngành nghề, khu vực địa lý hoạt động và nguồn gốc tài sản."
                ))
                .build();
        cif03.setProcedureSteps(Arrays.asList(
                ProcedureSection.builder()
                        .title("PHẦN 1: CÁN BỘ QTTD THỰC HIỆN (MAKER)")
                        .steps(Arrays.asList(
                                "1. ĐĂNG NHẬP & TÌM KIẾM KHÁCH HÀNG: Từ trang chủ WebCSR, sau khi đã tìm kiếm thành công khách hàng, thông tin khách hàng hiển thị tại thanh công cụ phía trên.",
                                "2. TRUY CẬP THÔNG TIN KHÁCH HÀNG: Chọn đường dẫn Thông tin khách hàng / Customer Information (hoặc từ menu Dịch vụ Khách hàng -> Thông tin khách hàng).",
                                "3. KHỞI TẠO VÀ NHẬP THÔNG TIN: Nhấn nút Tạo mới (Create). Điền thông tin pháp lý doanh nghiệp, người đại diện và tải lên tệp đính kèm hồ sơ doanh nghiệp.",
                                "4. GHI NHẬN & TRÌNH DUYỆT: Nhấn Lưu tạm để kiểm tra lỗi kiểm soát (Lỗi trùng mã số thuế, trùng tên...). Sau khi không còn cảnh báo hệ thống, nhấn Gửi duyệt để đẩy lên kiểm soát viên."
                        ))
                        .lesson(cif03)
                        .sectionOrder(0)
                        .build(),
                ProcedureSection.builder()
                        .title("PHẦN 2: KIỂM SOÁT VIÊN DUYỆT (CHECKER)")
                        .steps(Arrays.asList(
                                "1. TRUY VẤN HỒ SƠ CHỜ DUYỆT: Vào màn hình phê duyệt CIF, tìm kiếm theo mã hồ sơ hoặc tên doanh nghiệp.",
                                "2. ĐỐI CHIẾU HỒ SƠ GIẤY: Mở tệp đính kèm trên Core Banking và so sánh với hồ sơ bản cứng bao gồm Giấy phép ĐKKD, Điều lệ công ty.",
                                "3. PHÊ DUYỆT: Nếu thông tin khớp đúng, chọn Phê duyệt (Approve) để tạo mã CIF chính thức. Nếu sai sót, ghi rõ lý do và Từ chối (Reject) gửi trả về Maker."
                        ))
                        .lesson(cif03)
                        .sectionOrder(1)
                        .build()
        ));
        lessonRepository.save(cif03);

        // Add Comments to CIF03
        Comment c2 = Comment.builder()
                .id("c2")
                .username("nguyenvanb")
                .avatar("B")
                .rating(4)
                .content("Bài viết rất hữu ích, quy trình Maker/Checker viết rõ ràng.")
                .createdAt(LocalDateTime.now().minusDays(3))
                .lesson(cif03)
                .build();
        Comment c3 = Comment.builder()
                .id("c3")
                .username("lethic")
                .avatar("C")
                .rating(5)
                .content("Video trực quan, xem xong làm được ngay!")
                .createdAt(LocalDateTime.now().minusDays(1))
                .lesson(cif03)
                .build();
        commentRepository.save(c2);
        commentRepository.save(c3);

        // 3. Create Lessons for TIEN_VAY
        Lesson ln01 = Lesson.builder()
                .id("LN01")
                .title("LN01. Quy trình mở hồ sơ vay và phê duyệt tín dụng")
                .topic(tienVay)
                .hint("Menu: WebCSR -> Tín dụng -> Khởi tạo hồ sơ cho vay")
                .videoUrl("https://assets.mixkit.co/videos/preview/mixkit-startup-team-working-on-laptops-in-office-41553-large.mp4")
                .documentSlides(Arrays.asList(
                        "Hồ sơ tín dụng (Credit File) là tập hợp toàn bộ thông tin tài chính, pháp lý của khoản vay.",
                        "Thẩm quyền phê duyệt: Dựa trên phân cấp hạn mức tín dụng của Chi nhánh và Hội sở chính.",
                        "Kiểm tra tài sản bảo đảm: Phải được định giá độc lập và đăng ký giao dịch bảo đảm trước khi giải ngân."
                ))
                .build();
        ln01.setProcedureSteps(Arrays.asList(
                ProcedureSection.builder()
                        .title("Quy trình khởi tạo hồ sơ vay")
                        .steps(Arrays.asList(
                                "Nhập thông tin khách hàng vay vốn (CIF).",
                                "Khai báo thông tin khoản vay bao gồm: Số tiền đề nghị, mục đích sử dụng vốn, thời hạn vay, lãi suất vay.",
                                "Khai báo thông tin tài sản bảo đảm liên kết.",
                                "Tải lên báo cáo thẩm định tín dụng và trình duyệt cấp phê duyệt."
                        ))
                        .lesson(ln01)
                        .sectionOrder(0)
                        .build()
        ));
        lessonRepository.save(ln01);

        Lesson ln02 = Lesson.builder()
                .id("LN02")
                .title("LN02. Quy trình giải ngân khoản vay")
                .topic(tienVay)
                .hint("Menu: WebCSR -> Tín dụng -> Giải ngân khoản vay")
                .videoUrl("https://assets.mixkit.co/videos/preview/mixkit-executives-working-on-a-business-plan-41559-large.mp4")
                .documentSlides(Arrays.asList(
                        "Giải ngân (Disbursement) là bước chuyển vốn vay từ ngân hàng sang khách hàng.",
                        "Hạn mức và phân kỳ giải ngân: Giải ngân một lần hoặc nhiều lần theo tiến độ dự án.",
                        "Hạch toán kế toán: Nợ tài khoản cho vay (LN) / Có tài khoản tiền gửi thanh toán (DEP)."
                ))
                .build();
        ln02.setProcedureSteps(Arrays.asList(
                ProcedureSection.builder()
                        .title("Quy trình thực hiện tại quầy")
                        .steps(Arrays.asList(
                                "Kiểm tra chứng từ đề nghị giải ngân của khách hàng.",
                                "Thực hiện giao dịch giải ngân trên phân hệ LN.",
                                "In phiếu chi/phiếu chuyển khoản và trình Kiểm soát viên ký duyệt.",
                                "Giao tiền mặt hoặc chuyển khoản cho khách hàng."
                        ))
                        .lesson(ln02)
                        .sectionOrder(0)
                        .build()
        ));
        lessonRepository.save(ln02);

        // 4. Create Lessons for CHUYEN_TIEN
        Lesson tr01 = Lesson.builder()
                .id("TR01")
                .title("TR01. Quy trình chuyển tiền nhanh NAPAS 247")
                .topic(chuyenTien)
                .hint("Menu: WebCSR -> Chuyển tiền -> Chuyển tiền nhanh Napas 247")
                .videoUrl("https://assets.mixkit.co/videos/preview/mixkit-hands-of-a-businessman-working-on-a-laptop-42289-large.mp4")
                .documentSlides(Arrays.asList(
                        "Tổng quan về dịch vụ chuyển tiền nhanh NAPAS 247 trên Core Banking.",
                        "Hạn mức chuyển tiền tối đa cho một giao dịch tại quầy và các quy định về thu phí giao dịch.",
                        "Cơ chế đối soát tự động giữa BIDV và liên minh NAPAS.",
                        "Xử lý giao dịch lỗi: Các tình huống giao dịch trừ tiền tài khoản BIDV nhưng tài khoản thụ hưởng chưa nhận được."
                ))
                .build();
        tr01.setProcedureSteps(Arrays.asList(
                ProcedureSection.builder()
                        .title("Quy trình giao dịch viên (Maker)")
                        .steps(Arrays.asList(
                                "Nhập số tài khoản/số thẻ thụ hưởng và chọn ngân hàng đích.",
                                "Hệ thống tự động truy vấn và hiển thị tên chủ tài khoản thụ hưởng để đối chiếu.",
                                "Nhập số tiền và nội dung chuyển tiền.",
                                "Chọn phương thức thu phí (Người chuyển trả hoặc Người nhận trả) và xác nhận giao dịch."
                        ))
                        .lesson(tr01)
                        .sectionOrder(0)
                        .build()
        ));
        lessonRepository.save(tr01);

        // 5. Create Lessons for TIEN_GUI
        Lesson dep01 = Lesson.builder()
                .id("DEP01")
                .title("DEP01. Quy trình mở sổ tiết kiệm tại quầy")
                .topic(tienGui)
                .hint("Menu: WebCSR -> Tiền gửi -> Mở tài khoản tiết kiệm có kỳ hạn")
                .videoUrl("https://assets.mixkit.co/videos/preview/mixkit-business-people-meeting-around-a-table-41558-large.mp4")
                .documentSlides(Arrays.asList(
                        "Các loại hình tiền gửi tiết kiệm: Tiền gửi không kỳ hạn, Tiền gửi có kỳ hạn, Tiền gửi tích lũy.",
                        "Quy định về kỳ hạn, phương thức trả lãi (Đầu kỳ, Định kỳ, Cuối kỳ) và biểu lãi suất áp dụng.",
                        "Quy định pháp lý về người đứng tên sổ tiết kiệm và thủ tục mở sổ cho người nước ngoài.",
                        "Bảo mật và lưu kho phôi sổ tiết kiệm trắng."
                ))
                .build();
        dep01.setProcedureSteps(Arrays.asList(
                ProcedureSection.builder()
                        .title("Quy trình mở sổ (Giao dịch viên)")
                        .steps(Arrays.asList(
                                "Nhận yêu cầu và kiểm tra giấy tờ tùy thân của khách hàng (CCCD/Hộ chiếu còn hạn).",
                                "Quét và cập nhật thông tin khách hàng (nếu chưa có CIF) hoặc truy vấn CIF.",
                                "Nhập các tham số sổ tiết kiệm: Số tiền, Kỳ hạn, Phương thức nhận lãi, Chỉ thị tái tục.",
                                "In thẻ tiết kiệm từ phôi trắng, đóng dấu và trình Kiểm soát viên duyệt."
                        ))
                        .lesson(dep01)
                        .sectionOrder(0)
                        .build()
        ));
        lessonRepository.save(dep01);

        // 6. Create Lessons for TELLER
        Lesson tel01 = Lesson.builder()
                .id("TEL01")
                .title("TEL01. Quy trình quản lý quỹ đầu ngày và cuối ngày")
                .topic(teller)
                .hint("Menu: WebCSR -> Teller -> Quản lý két tiền mặt")
                .videoUrl("https://assets.mixkit.co/videos/preview/mixkit-business-team-in-a-meeting-working-41563-large.mp4")
                .documentSlides(Arrays.asList(
                        "Giao dịch viên (Teller) chịu trách nhiệm quản lý hạn mức tồn quỹ tiền mặt được giao.",
                        "Đầu ngày: Nhận bàn giao tiền mặt từ thủ quỹ chi nhánh và kiểm đếm nhập két.",
                        "Trong ngày: Thực hiện các giao dịch thu chi trong hạn mức. Nếu vượt hạn mức phải làm thủ tục đẩy tiền về quỹ chính.",
                        "Cuối ngày: Kiểm kê tồn quỹ thực tế tại két, đối chiếu với số dư sổ sách trên Core Banking."
                ))
                .build();
        tel01.setProcedureSteps(Arrays.asList(
                ProcedureSection.builder()
                        .title("Quy trình cuối ngày (Thực hiện bởi Giao dịch viên)")
                        .steps(Arrays.asList(
                                "Chạy báo cáo liệt kê tất cả giao dịch thu chi tiền mặt trong ngày.",
                                "Thực hiện kiểm đếm tiền mặt thực tế theo từng mệnh giá.",
                                "Nhập số liệu kiểm đếm vào giao dịch đóng số dư cuối ngày.",
                                "In nhật ký giao dịch và biên bản kiểm kê, ký tên xác nhận và niêm phong két."
                        ))
                        .lesson(tel01)
                        .sectionOrder(0)
                        .build()
        ));
        lessonRepository.save(tel01);

        // 7. Create Lessons for GL
        Lesson gl01 = Lesson.builder()
                .id("GL01")
                .title("GL01. Quy trình hạch toán bút toán thủ công")
                .topic(gl)
                .hint("Menu: WebCSR -> Sổ cái (GL) -> Tạo bút toán thủ công")
                .videoUrl("https://assets.mixkit.co/videos/preview/mixkit-group-of-businesspeople-discussing-charts-41565-large.mp4")
                .documentSlides(Arrays.asList(
                        "Phân hệ Sổ cái (General Ledger - GL) ghi nhận tất cả nghiệp vụ kế toán phát sinh của ngân hàng.",
                        "Bút toán thủ công (Manual Journal Entry) chỉ được dùng khi xử lý các nghiệp vụ đặc thù hoặc điều chỉnh sai sót kế toán.",
                        "Quy tắc hạch toán kép: Tổng số tiền phát sinh Nợ phải luôn bằng Tổng số tiền phát sinh Có.",
                        "Mã tài khoản GL: Hệ thống tài khoản kế toán BIDV tuân thủ quy định của Ngân hàng Nhà nước."
                ))
                .build();
        gl01.setProcedureSteps(Arrays.asList(
                ProcedureSection.builder()
                        .title("Quy trình tạo bút toán điều chỉnh (Maker)")
                        .steps(Arrays.asList(
                                "Xác định nguyên nhân sai sót và tài khoản GL cần điều chỉnh.",
                                "Vào phân hệ GL, chọn loại bút toán điều chỉnh.",
                                "Nhập tài khoản Nợ, tài khoản Có và số tiền tương ứng.",
                                "Nhập nội dung diễn giải chi tiết nguyên nhân điều chỉnh và trình duyệt."
                        ))
                        .lesson(gl01)
                        .sectionOrder(0)
                        .build()
        ));
        lessonRepository.save(gl01);

        // 8. Create Lessons for THE
        Lesson card01 = Lesson.builder()
                .id("CARD01")
                .title("CARD01. Quy trình phát hành thẻ ghi nợ nội địa")
                .topic(the)
                .hint("Menu: WebCSR -> Thẻ -> Phát hành thẻ ghi nợ")
                .videoUrl("https://assets.mixkit.co/videos/preview/mixkit-colleagues-discussing-data-on-a-monitor-41567-large.mp4")
                .documentSlides(Arrays.asList(
                        "Thẻ ghi nợ nội địa (BIDV Smart/Harmony) liên kết trực tiếp với tài khoản thanh toán của khách hàng.",
                        "Điều kiện phát hành: Khách hàng đã có CIF và tài khoản tiền gửi thanh toán hoạt động bình thường.",
                        "Phân loại phát hành: Phát hành thẻ vật lý (lấy ngay hoặc nhận sau) và phát hành thẻ phi vật lý trên ứng dụng Smart OTP/Smart Banking."
                ))
                .build();
        card01.setProcedureSteps(Arrays.asList(
                ProcedureSection.builder()
                        .title("Quy trình đăng ký phát hành thẻ tại quầy")
                        .steps(Arrays.asList(
                                "Nhận biểu mẫu đăng ký phát hành thẻ của khách hàng.",
                                "Đối chiếu chữ ký trên biểu mẫu với chữ ký mẫu đã đăng ký trên Core Banking.",
                                "Nhập mã CIF, chọn loại thẻ phát hành và liên kết tài khoản thanh toán mặc định.",
                                "Xác nhận thông tin địa chỉ nhận thẻ (tại chi nhánh hoặc chuyển phát nhanh về nhà) và hoàn thành giao dịch."
                        ))
                        .lesson(card01)
                        .sectionOrder(0)
                        .build()
        ));
        lessonRepository.save(card01);


        // 9. Initialize Questions for Quiz
        // CIF Questions
        createQuestion(cif, "Mã phân hệ nào dùng để Quản lý Khách hàng trong Core Banking?", Arrays.asList("CIF", "DEP", "LN", "GL"), 0);
        createQuestion(cif, "Thông tin nào sau đây là BẮT BUỘC khi khởi tạo CIF cho Khách hàng doanh nghiệp?", Arrays.asList("Số điện thoại nhân viên", "Mã số thuế doanh nghiệp", "Mã chứng khoán niêm yết", "Danh sách cổ đông liên kết"), 1);
        createQuestion(cif, "Để tránh trùng lặp dữ liệu khách hàng cá nhân (trùng CIF), giao dịch viên cần làm gì trước khi tạo mới?", Arrays.asList("Tìm kiếm kỹ theo Số giấy tờ tùy thân", "Tìm kiếm theo Tên không dấu", "Tạo ngẫu nhiên một mã CIF mới", "Liên hệ chi nhánh khác hỏi thông tin"), 0);
        createQuestion(cif, "Trong quy trình tạo CIF Khách hàng tổ chức, bước phê duyệt cuối cùng do vai trò nào thực hiện?", Arrays.asList("Maker (Giao dịch viên)", "Checker (Kiểm soát viên)", "Thủ quỹ", "Khách hàng"), 1);
        createQuestion(cif, "Sau khi quét mã QR trên CCCD gắn chip của khách hàng cá nhân, hệ thống Core Banking sẽ làm gì?", Arrays.asList("Tự động cấp tín dụng cho khách hàng", "Tự động gửi tin nhắn SMS chúc mừng", "Tự động trích xuất và điền các thông tin cơ bản của khách hàng", "Tự động phong tỏa tài khoản thanh toán"), 2);

        // TIEN_VAY Questions
        createQuestion(tienVay, "Ký hiệu phân hệ cho vay trong Core Banking của BIDV là gì?", Arrays.asList("DEP", "LN", "GL", "TR"), 1);
        createQuestion(tienVay, "Quy trình vòng đời cơ bản của một khoản vay trên Core Banking diễn ra như thế nào?", Arrays.asList(
                "Giải ngân -> Tạo hồ sơ -> Tất toán -> Phê duyệt",
                "Tạo hồ sơ -> Phê duyệt -> Giải ngân -> Thu nợ/lãi -> Tất toán",
                "Phê duyệt -> Tạo hồ sơ -> Giải ngân -> Tất toán",
                "Giải ngân -> Thu nợ/lãi -> Tạo hồ sơ -> Tất toán"
        ), 1);
        createQuestion(tienVay, "Bút toán hạch toán kế toán khi giải ngân khoản vay vào tài khoản thanh toán của khách hàng là:", Arrays.asList(
                "Nợ TK Tiền gửi / Có TK Cho vay",
                "Nợ TK Sổ cái / Có TK Thu nhập",
                "Nợ TK Cho vay (LN) / Có TK Tiền gửi thanh toán (DEP)",
                "Nợ TK Tiền mặt / Có TK Cho vay"
        ), 2);
        createQuestion(tienVay, "Ai là người chịu trách nhiệm nhập liệu thông tin khoản vay ban đầu trên hệ thống?", Arrays.asList("Checker", "Maker (Cán bộ QTTD)", "Thủ quỹ chi nhánh", "Giám đốc Chi nhánh"), 1);
        createQuestion(tienVay, "Điều kiện nào sau đây KHÔNG bắt buộc khi thực hiện giải ngân khoản vay thông thường?", Arrays.asList(
                "Khoản vay đã được phê duyệt hợp lệ",
                "Tài sản bảo đảm đã được phong tỏa trên hệ thống",
                "Khách hàng phải có tài sản tiết kiệm tại ngân hàng",
                "Hợp đồng thế chấp đã ký kết đúng quy định"
        ), 2);

        // CHUYEN_TIEN Questions
        createQuestion(chuyenTien, "Hạn mức chuyển tiền nhanh NAPAS 247 tối đa cho một giao dịch tại quầy hiện nay là bao nhiêu?", Arrays.asList("100 triệu VND", "300 triệu VND", "499.999.999 VND", "1 tỷ VND"), 2);
        createQuestion(chuyenTien, "Khi giao dịch viên nhập số tài khoản thụ hưởng trên màn hình NAPAS 247, hệ thống Core Banking sẽ thực hiện điều gì?", Arrays.asList(
                "Tự động trừ tiền ngay lập tức",
                "Tự động truy vấn hiển thị tên chủ tài khoản thụ hưởng tại ngân hàng đích",
                "Tự động gửi mã OTP về số điện thoại khách hàng",
                "Yêu cầu tải lên hồ sơ chứng từ đính kèm"
        ), 1);
        createQuestion(chuyenTien, "Lỗi giao dịch trừ tiền tài khoản người chuyển nhưng tài khoản thụ hưởng chưa nhận được tiền thường được giải quyết qua quy trình nào?", Arrays.asList("Tra soát và đối soát định kỳ với Napas", "Tự động hoàn tiền sau 5 phút", "Yêu cầu khách hàng tự liên hệ ngân hàng nhận", "Hạch toán bút toán thủ công ngay"), 0);
        createQuestion(chuyenTien, "Phương thức thu phí chuyển tiền nào có thể được lựa chọn khi thực hiện giao dịch tại quầy?", Arrays.asList(
                "Chỉ người chuyển trả phí",
                "Chỉ người nhận trả phí",
                "Người chuyển trả phí HOẶC Người nhận trả phí",
                "Ngân hàng chịu toàn bộ chi phí"
        ), 2);
        createQuestion(chuyenTien, "Nếu chuyển tiền nhầm tài khoản, giao dịch viên cần tư vấn cho khách hàng quy trình nào đầu tiên?", Arrays.asList("Lập tra soát hoàn trả gửi ngân hàng bạn", "Tự ý thu hồi tiền từ tài khoản người nhận", "Cung cấp thông tin cá nhân của người nhận nhầm cho khách hàng", "Kêu gọi lực lượng an ninh phong tỏa tài khoản"), 0);

        // TIEN_GUI Questions
        createQuestion(tienGui, "Phân hệ Tiền gửi trên Core Banking thường được viết tắt là gì?", Arrays.asList("LN", "DEP", "GL", "TR"), 1);
        createQuestion(tienGui, "Lãi suất tiền gửi tiết kiệm có kỳ hạn thường được tính toán dựa trên cơ chế nào?", Arrays.asList("Tự thỏa thuận không giới hạn", "Biểu lãi suất quy định tại thời điểm mở sổ", "Thay đổi tự do hàng ngày", "Chỉ tính lãi suất không kỳ hạn"), 1);
        createQuestion(tienGui, "Khách hàng rút tiền tiết kiệm trước kỳ hạn (đối với sản phẩm thông thường) sẽ hưởng lãi suất gì?", Arrays.asList("Lãi suất không kỳ hạn", "Không hưởng lãi", "Hưởng 50% lãi suất có kỳ hạn", "Vẫn hưởng nguyên lãi suất kỳ hạn"), 0);
        createQuestion(tienGui, "Quy định nào sau đây là bắt buộc khi làm thủ tục mở sổ tiết kiệm đồng sở hữu?", Arrays.asList("Chỉ cần một người đại diện ký tên", "Tất cả các chủ sở hữu phải có mặt hoặc ủy quyền hợp pháp", "Không cho phép mở sổ đồng sở hữu", "Chỉ áp dụng cho người nước ngoài"), 1);
        createQuestion(tienGui, "Hành vi nào dưới đây vi phạm an toàn kho quỹ đối với phôi sổ tiết kiệm trắng?", Arrays.asList("Lưu kho khóa cẩn thận", "Bàn giao có biên bản kiểm đếm", "Để phôi sổ tự do trên bàn làm việc không có sự kiểm soát", "Kiểm kê định kỳ cuối ngày"), 2);

        // TELLER Questions
        createQuestion(teller, "Hạn mức tồn quỹ của Giao dịch viên tại quầy được thiết lập nhằm mục đích gì?", Arrays.asList("Hạn chế giao dịch lớn", "Giảm thiểu rủi ro thất thoát tiền mặt tại quầy", "Tăng doanh số chuyển tiền", "Bảo vệ thông tin khách hàng"), 1);
        createQuestion(teller, "Giao dịch viên phải làm gì khi phát hiện tồn quỹ thực tế cuối ngày lệch (thừa hoặc thiếu) so với sổ sách?", Arrays.asList("Tự bù tiền thiếu hoặc đút túi tiền thừa", "Ghi nhận treo chênh lệch và báo cáo Kiểm soát viên/Ban Giám đốc tìm nguyên nhân", "Điều chỉnh số liệu báo cáo cho khớp", "Không làm gì cả và ra về"), 1);
        createQuestion(teller, "Ai là người bàn giao tiền mặt đầu ngày cho Giao dịch viên?", Arrays.asList("Giám đốc Chi nhánh", "Khách hàng", "Thủ quỹ chi nhánh", "Giao dịch viên khác"), 2);
        createQuestion(teller, "Trong ngày, nếu cần tiền mặt vượt hạn mức quỹ, Teller phải thực hiện giao dịch nào?", Arrays.asList("Giao dịch xin tạm ứng quỹ từ thủ quỹ (Cash Advance)", "Tự ý mở két chính để lấy", "Yêu cầu khách hàng nộp thêm", "Không phục vụ khách hàng"), 0);
        createQuestion(teller, "Quy trình niêm phong két tiền mặt cuối ngày đòi hỏi chữ ký của những ai trên niêm phong?", Arrays.asList("Chỉ Teller", "Teller và Thủ quỹ", "Teller và Kiểm soát viên hoặc người chứng kiến đại diện", "Giám đốc Chi nhánh"), 2);

        // GL Questions
        createQuestion(gl, "Bút toán điều chỉnh thủ công (Manual Journal Entry) được tạo ra bởi vai trò nào?", Arrays.asList("Checker", "Thủ quỹ", "Maker (Giao dịch viên/Kế toán)", "Khách hàng"), 2);
        createQuestion(gl, "Theo quy tắc kế toán kép, phát biểu nào sau đây là luôn luôn đúng?", Arrays.asList("Tổng Nợ > Tổng Có", "Tổng Nợ < Tổng Có", "Tổng Nợ = Tổng Có", "Không cần bằng nhau"), 2);
        createQuestion(gl, "Khi tạo bút toán GL thủ công, phần nội dung diễn giải (Description) có bắt buộc không?", Arrays.asList("Không bắt buộc", "Bắt buộc để giải trình nguyên nhân và mục đích", "Chỉ bắt buộc đối với giao dịch ngoại tệ", "Tùy ý thích của Maker"), 1);
        createQuestion(gl, "Bút toán hạch toán chuyển tiền nội bộ giữa các tài khoản GL phải đảm bảo nguyên tắc gì?", Arrays.asList("Chỉ ghi nhận bên Nợ", "Chỉ ghi nhận bên Có", "Đúng mã tài khoản GL và cân bằng Nợ/Có", "Chuyển tiền ra nước ngoài"), 2);
        createQuestion(gl, "Vai trò phê duyệt bút toán GL thủ công trên Core Banking là:", Arrays.asList("Teller", "Kiểm soát viên (Checker) hoặc cấp thẩm quyền tương đương", "Khách hàng", "Người tạo lập"), 1);

        // THE Questions
        createQuestion(the, "Thẻ ghi nợ nội địa liên kết trực tiếp với loại tài khoản nào?", Arrays.asList("Tài khoản tiền gửi thanh toán (DEP)", "Tài khoản cho vay (LN)", "Tài khoản Sổ cái (GL)", "Tài khoản Thẻ tín dụng"), 0);
        createQuestion(the, "Điều kiện đầu tiên để một khách hàng cá nhân được phát hành thẻ ghi nợ nội địa là gì?", Arrays.asList("Đã có mã CIF và tài khoản thanh toán tại ngân hàng", "Đang có khoản vay nợ", "Đạt điểm số học tập cao", "Phải nộp ký quỹ 10 triệu"), 0);
        createQuestion(the, "Phát hành thẻ phi vật lý (Virtual Card) mang lại lợi ích gì?", Arrays.asList("Khách hàng phải chờ 7 ngày lấy thẻ", "Khách hàng có thể sử dụng ngay trên ứng dụng ngân hàng số, tiết kiệm chi phí phôi thẻ", "Thẻ chỉ dùng để rút tiền tại cây ATM", "Phí phát hành đắt hơn thẻ vật lý"), 1);
        createQuestion(the, "Chữ ký của khách hàng trên biểu mẫu đề nghị phát hành thẻ phải trùng khớp với:", Arrays.asList("Chữ ký của bất kỳ ai", "Chữ ký mẫu đã đăng ký của chính khách hàng đó trên hệ thống", "Chữ ký của Giao dịch viên", "Không cần chữ ký"), 1);
        createQuestion(the, "Thẻ ghi nợ vật lý gửi về chi nhánh nếu khách hàng không nhận sau 3-6 tháng thì xử lý thế nào?", Arrays.asList("Tự ý mang đi tiêu dùng", "Trả lại nhà máy in", "Thực hiện quy trình hủy thẻ vô hiệu hóa bảo mật theo quy định", "Bán lại cho người khác"), 2);


        // 10. Create default Badges for topics
        createBadge(cif, "CIF_gold", "Huy hiệu Vàng CIF", "gold", "Award");
        createBadge(cif, "CIF_silver", "Huy hiệu Bạc CIF", "silver", "Medal");
        createBadge(cif, "CIF_bronze", "Huy hiệu Đồng CIF", "bronze", "Shield");

        createBadge(tienGui, "TIEN_GUI_gold", "Huy hiệu Vàng Tiền Gửi", "gold", "Award");
        createBadge(tienGui, "TIEN_GUI_silver", "Huy hiệu Bạc Tiền Gửi", "silver", "Medal");
        createBadge(tienGui, "TIEN_GUI_bronze", "Huy hiệu Đồng Tiền Gửi", "bronze", "Shield");

        createBadge(tienVay, "TIEN_VAY_gold", "Huy hiệu Vàng Tiền Vay", "gold", "Award");
        createBadge(tienVay, "TIEN_VAY_silver", "Huy hiệu Bạc Tiền Vay", "silver", "Medal");
        createBadge(tienVay, "TIEN_VAY_bronze", "Huy hiệu Đồng Tiền Vay", "bronze", "Shield");

        createBadge(chuyenTien, "CHUYEN_TIEN_gold", "Huy hiệu Vàng Chuyển Tiền", "gold", "Award");
        createBadge(chuyenTien, "CHUYEN_TIEN_silver", "Huy hiệu Bạc Chuyển Tiền", "silver", "Medal");
        createBadge(chuyenTien, "CHUYEN_TIEN_bronze", "Huy hiệu Đồng Chuyển Tiền", "bronze", "Shield");

        createBadge(teller, "TELLER_gold", "Huy hiệu Vàng Teller", "gold", "Award");
        createBadge(teller, "TELLER_silver", "Huy hiệu Bạc Teller", "silver", "Medal");
        createBadge(teller, "TELLER_bronze", "Huy hiệu Đồng Teller", "bronze", "Shield");

        createBadge(gl, "GL_gold", "Huy hiệu Vàng GL", "gold", "Award");
        createBadge(gl, "GL_silver", "Huy hiệu Bạc GL", "silver", "Medal");
        createBadge(gl, "GL_bronze", "Huy hiệu Đồng GL", "bronze", "Shield");

        createBadge(the, "THE_gold", "Huy hiệu Vàng Thẻ", "gold", "Award");
        createBadge(the, "THE_silver", "Huy hiệu Bạc Thẻ", "silver", "Medal");
        createBadge(the, "THE_bronze", "Huy hiệu Đồng Thẻ", "bronze", "Shield");


        // 11. Create Users (with password encrypted)
        User adminUser = userRepository.save(User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .fullName("Quản trị hệ thống")
                .avatarColor("#EF4444")
                .employeeId("ADMIN")
                .email("admin@bidv.com.vn")
                .role("ROLE_ADMIN")
                .points(0)
                .build());

        User systemUser = userRepository.save(User.builder()
                .username("system")
                .password(passwordEncoder.encode("123@qwe"))
                .fullName("Hệ Thống")
                .avatarColor("#8B5CF6")
                .employeeId("SYSTEM")
                .email("system@bidv.com.vn")
                .role("ROLE_USER")
                .points(2000)
                .userGroup(glGroup)
                .build());

        User quantrh = userRepository.save(User.builder()
                .username("quantrh")
                .password(passwordEncoder.encode("123456"))
                .fullName("Quản Trị Hệ Thống (Bạn)")
                .avatarColor("#005B41")
                .employeeId("BIDV001")
                .email("quantrh@bidv.com.vn")
                .role("ROLE_USER")
                .points(1850)
                .userGroup(cifGroup)
                .build());

        User userA = userRepository.save(User.builder()
                .username("nguyenvana")
                .password(passwordEncoder.encode("123456"))
                .fullName("Nguyễn Văn An")
                .avatarColor("#4F46E5")
                .employeeId("BIDV002")
                .email("nguyenvana@bidv.com.vn")
                .role("ROLE_USER")
                .points(2850)
                .userGroup(cifGroup)
                .build());

        User userB = userRepository.save(User.builder()
                .username("tranb")
                .password(passwordEncoder.encode("123456"))
                .fullName("Trần Thị Bình")
                .avatarColor("#10B981")
                .employeeId("BIDV003")
                .email("tranb@bidv.com.vn")
                .role("ROLE_USER")
                .points(2600)
                .userGroup(tienVayGroup)
                .build());

        User userC = userRepository.save(User.builder()
                .username("lephuc")
                .password(passwordEncoder.encode("123456"))
                .fullName("Lê Hoàng Phúc")
                .avatarColor("#F59E0B")
                .employeeId("BIDV004")
                .email("lephuc@bidv.com.vn")
                .role("ROLE_USER")
                .points(2450)
                .userGroup(chuyenTienGroup)
                .build());

        User userD = userRepository.save(User.builder()
                .username("phamduc")
                .password(passwordEncoder.encode("123456"))
                .fullName("Phạm Minh Đức")
                .avatarColor("#EF4444")
                .employeeId("BIDV005")
                .email("phamduc@bidv.com.vn")
                .role("ROLE_USER")
                .points(2100)
                .userGroup(tienGuiGroup)
                .build());

        User userE = userRepository.save(User.builder()
                .username("hoangyen")
                .password(passwordEncoder.encode("123456"))
                .fullName("Hoàng Kim Yến")
                .avatarColor("#EC4899")
                .employeeId("BIDV006")
                .email("hoangyen@bidv.com.vn")
                .role("ROLE_USER")
                .points(1500)
                .userGroup(tellerGroup)
                .build());

        User userF = userRepository.save(User.builder()
                .username("vuongminh")
                .password(passwordEncoder.encode("123456"))
                .fullName("Vương Tuấn Minh")
                .avatarColor("#8B5CF6")
                .employeeId("BIDV007")
                .email("vuongminh@bidv.com.vn")
                .role("ROLE_USER")
                .points(1200)
                .userGroup(theGroup)
                .build());


        // 12. Create Progress and achievements for 'quantrh' (current user mock progress)
        // Completed lessons
        quantrh.getCompletedLessons().add(cif01);
        quantrh.getCompletedLessons().add(dep01);
        userRepository.save(quantrh);

        // Prepopulate scores
        userQuizScoreRepository.save(UserQuizScore.builder().user(quantrh).topic(cif).score(4).total(5).takenAt(LocalDateTime.now().minusDays(3)).build());
        userQuizScoreRepository.save(UserQuizScore.builder().user(quantrh).topic(tienGui).score(4).total(5).takenAt(LocalDateTime.now().minusDays(2)).build());

        // Prepopulate badges
        Badge cifBronze = badgeRepository.findById("CIF_bronze").orElseThrow();
        Badge tienGuiBronze = badgeRepository.findById("TIEN_GUI_bronze").orElseThrow();
        userBadgeRepository.save(UserBadge.builder().user(quantrh).badge(cifBronze).unlockedAt(LocalDate.now().minusDays(3)).build());
        userBadgeRepository.save(UserBadge.builder().user(quantrh).badge(tienGuiBronze).unlockedAt(LocalDate.now().minusDays(2)).build());


        // 13. Prepopulate scores and badges for Leaderboard users (to make ranks work correctly)
        // User A (2850 pts, completed 8, badges 6)
        setupMockUserProgress(userA, Arrays.asList(cif01, cif03, ln01, ln02, tr01, dep01, tel01, gl01), Arrays.asList(
                "CIF_gold", "TIEN_GUI_gold", "TIEN_VAY_gold", "CHUYEN_TIEN_silver", "TELLER_silver", "GL_bronze"
        ));

        // User B (2600 pts, completed 7, badges 5)
        setupMockUserProgress(userB, Arrays.asList(cif01, cif03, ln01, ln02, tr01, dep01, tel01), Arrays.asList(
                "CIF_gold", "TIEN_GUI_gold", "TIEN_VAY_silver", "CHUYEN_TIEN_silver", "TELLER_bronze"
        ));

        // User C (2450 pts, completed 6, badges 4)
        setupMockUserProgress(userC, Arrays.asList(cif01, cif03, ln01, ln02, tr01, dep01), Arrays.asList(
                "CIF_gold", "TIEN_GUI_silver", "TIEN_VAY_silver", "CHUYEN_TIEN_bronze"
        ));

        // User D (2100 pts, completed 5, badges 3)
        setupMockUserProgress(userD, Arrays.asList(cif01, cif03, ln01, ln02, tr01), Arrays.asList(
                "CIF_gold", "TIEN_VAY_silver", "CHUYEN_TIEN_bronze"
        ));

        // User E (1500 pts, completed 3, badges 2)
        setupMockUserProgress(userE, Arrays.asList(cif01, cif03, ln01), Arrays.asList(
                "CIF_silver", "TIEN_VAY_bronze"
        ));

        // User F (1200 pts, completed 2, badges 1)
        setupMockUserProgress(userF, Arrays.asList(cif01, dep01), Arrays.asList(
                "CIF_bronze"
        ));

        // Force all initially seeded database users to have mustChangePassword = false
        userRepository.findAll().forEach(u -> {
            u.setMustChangePassword(false);
            userRepository.save(u);
        });
    }

    private void createQuestion(Topic topic, String text, List<String> options, int correctIndex) {
        questionRepository.save(Question.builder()
                .topic(topic)
                .text(text)
                .options(options)
                .correctAnswer(correctIndex)
                .build());
    }

    private void createBadge(Topic topic, String id, String title, String type, String icon) {
        badgeRepository.save(Badge.builder()
                .id(id)
                .title(title)
                .type(type)
                .topic(topic)
                .icon(icon)
                .build());
    }

    private void setupMockUserProgress(User user, List<Lesson> completedLessons, List<String> badgeIds) {
        user.setCompletedLessons(new HashSet<>(completedLessons));
        userRepository.save(user);

        for (String badgeId : badgeIds) {
            Badge badge = badgeRepository.findById(badgeId).orElse(null);
            if (badge != null) {
                userBadgeRepository.save(UserBadge.builder()
                        .user(user)
                        .badge(badge)
                        .unlockedAt(LocalDate.now().minusDays(5))
                        .build());
            }
        }
    }
}
