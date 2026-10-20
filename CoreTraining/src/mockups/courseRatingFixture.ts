import { reportFixture } from './reportFixture';
import type { CourseRating } from '../utils/courseRatingMetrics';
// Separate design data for the development preview; live reports use CourseRating API.
export const courseRatingFixture:CourseRating[]=reportFixture.filter(row=>row.status==='Hoàn thành').filter((_,index)=>index!==0).map((row,index)=>({
  id:`course-review-${index}`,learnerId:row.learnerId,courseName:row.courseName,scope:'COURSE',parentId:null,
  stars:[5,2,4][index%3]!,createdAt:`${row.date.slice(0,7)}-15T09:00:00`,
  content:['Nội dung dễ hiểu, ví dụ sát với công việc.','Cần thêm tình huống thực hành để áp dụng.','Bài học rõ ràng, tài liệu hữu ích.','Một số nội dung cần cập nhật và giải thích kỹ hơn.','Khóa học phù hợp với nhu cầu của tôi.','Video khó theo dõi, cần chia thành các đoạn ngắn hơn.','Có thể bổ sung phần tổng kết cuối khóa.','Tài liệu chưa rõ, tôi cần thêm hướng dẫn thực hành.'][index%8]!,
}));
