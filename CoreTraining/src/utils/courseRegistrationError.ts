export function courseRegistrationError(error: unknown): string {
  const response = (error as {
    response?: { status?: number; data?: { message?: unknown } };
  } | null)?.response;
  const message = response?.data?.message;
  if (typeof message === "string" && message.trim()
      && !["access denied", "forbidden"].includes(message.trim().toLowerCase())) {
    return message;
  }
  if (response?.status === 403) {
    return "Bạn chưa có quyền đăng ký khóa học này. Vui lòng liên hệ quản trị viên để được cấp quyền.";
  }
  return "Không gửi được yêu cầu đăng ký. Vui lòng thử lại sau.";
}
