package task120601;
import java.sql.*;
import java.util.Scanner;

public class DBtask0031206 {
	
	//MySQL 연결 정보
		static final String DB_URL = "jdbc:mysql://localhost/causwtask1206?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC"; //데이터베이스 주소
		static final String USER = "root"; //MySQL 사용자 이름
		static final String PASS = "mysql"; //MySQL 비밀번호

		 public static void main(String[] args) {
		        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
		            Scanner scanner = new Scanner(System.in);
		            
		            while (true) {
		                System.out.println("\n=== 도서 관리 시스템 ===");
		                System.out.println("1. 참고도서 조회");
		                System.out.println("2. 도서 대출");
		                System.out.println("3. 진로통계 조회");
		                System.out.println("4. 종료");
		                System.out.print("메뉴 선택: ");
		                
		                int choice = scanner.nextInt();
		                scanner.nextLine(); // 버퍼 비우기
		                
		                switch (choice) {
		                    case 1:
		                        queryReferenceBooks(conn, scanner);
		                        break;
		                    case 2:
		                        borrowBook(conn, scanner);
		                        break;
		                    case 3:
		                        queryCareerStatistics(conn, scanner);
		                        break;
		                    case 4:
		                        System.out.println("프로그램을 종료합니다.");
		                        return;
		                    default:
		                        System.out.println("잘못된 선택입니다.");
		                }
		            }
		        } catch (SQLException e) {
		            e.printStackTrace();
		        }
		    }

		    // 1. 참고도서 조회
		    private static void queryReferenceBooks(Connection conn, Scanner scanner) throws SQLException {
		        System.out.print("과목 ID: ");
		        String courseId = scanner.nextLine();
		        System.out.print("분반 ID: ");
		        String secId = scanner.nextLine();
		        System.out.print("학기: ");
		        String semester = scanner.nextLine();
		        System.out.print("년도: ");
		        int year = scanner.nextInt();

		        String sql = "SELECT r.ISBN, r.title, r.author, r.publisher, r.year, i.name as instructor_name " +
		                    "FROM reference_book r " +
		                    "JOIN refers ref ON r.ISBN = ref.ISBN " +
		                    "JOIN teaches t ON ref.course_id = t.course_id " +
		                    "AND ref.sec_id = t.sec_id " +
		                    "AND ref.semester = t.semester " +
		                    "AND ref.year = t.year " +
		                    "JOIN instructor i ON t.ID = i.ID " +
		                    "WHERE ref.course_id = ? " +
		                    "AND ref.sec_id = ? " +
		                    "AND ref.semester = ? " +
		                    "AND ref.year = ?";

		        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
		            pstmt.setString(1, courseId);
		            pstmt.setString(2, secId);
		            pstmt.setString(3, semester);
		            pstmt.setInt(4, year);

		            ResultSet rs = pstmt.executeQuery();
		            boolean found = false;
		            
		            while (rs.next()) {
		                found = true;
		                System.out.printf("\n담당교수: %s\n", rs.getString("instructor_name"));
		                System.out.printf("ISBN: %s\n", rs.getString("ISBN"));
		                System.out.printf("제목: %s\n", rs.getString("title"));
		                System.out.printf("저자: %s\n", rs.getString("author"));
		                System.out.printf("출판사: %s\n", rs.getString("publisher"));
		                System.out.printf("출판년도: %d\n", rs.getInt("year"));
		                System.out.println("------------------------");
		            }
		            
		            if (!found) {
		                System.out.println("해당하는 참고도서가 없습니다.");
		            }
		        }
		    }

		    // 2. 도서 대출
		    private static void borrowBook(Connection conn, Scanner scanner) throws SQLException {
		        System.out.print("학번: ");
		        String studentId = scanner.nextLine();
		        System.out.print("ISBN: ");
		        String isbn = scanner.nextLine();

		        // 수강생 확인
		        String checkEnrollmentSql = 
		            "SELECT t.ID FROM takes t " +
		            "JOIN refers r ON t.course_id = r.course_id " +
		            "AND t.sec_id = r.sec_id " +
		            "AND t.semester = r.semester " +
		            "AND t.year = r.year " +
		            "WHERE t.ID = ? AND r.ISBN = ?";

		        try (PreparedStatement pstmt = conn.prepareStatement(checkEnrollmentSql)) {
		            pstmt.setString(1, studentId);
		            pstmt.setString(2, isbn);
		            
		            ResultSet rs = pstmt.executeQuery();
		            if (!rs.next()) {
		                System.out.println("대출불허: 해당 도서의 수강생이 아닙니다.");
		                return;
		            }
		        }

		        // 대출 정보 입력
		        String borrowSql = 
		            "INSERT INTO borrow_info (ISBN, ID, borrow_date, return_date, name, number, course_id, sec_id, semester, year) " +
		            "SELECT ?, ?, ?, ?, s.name, '010-1234-5678', r.course_id, r.sec_id, r.semester, r.year " +
		            "FROM student s, refers r " +
		            "WHERE s.ID = ? AND r.ISBN = ?";

		        try (PreparedStatement pstmt = conn.prepareStatement(borrowSql)) {
		            int borrowDate = Integer.parseInt(String.format("%tY%<tm%<td", new java.util.Date()));
		            int returnDate = borrowDate + 7; // 7일 후 반납

		            pstmt.setString(1, isbn);
		            pstmt.setString(2, studentId);
		            pstmt.setInt(3, borrowDate);
		            pstmt.setInt(4, returnDate);
		            pstmt.setString(5, studentId);
		            pstmt.setString(6, isbn);

		            int result = pstmt.executeUpdate();
		            if (result > 0) {
		                System.out.println("대출완료");
		                System.out.println("대출일: " + borrowDate);
		                System.out.println("반납예정일: " + returnDate);
		            }
		        }
		    }

		    // 3. 진로통계 조회
		    private static void queryCareerStatistics(Connection conn, Scanner scanner) throws SQLException {
		        System.out.print("학과명: ");
		        String deptName = scanner.nextLine();

		        String sql = "SELECT year, month, get_job, school, startup, etc " +
		                    "FROM career_statistics " +
		                    "WHERE dept_name = ? " +
		                    "ORDER BY year DESC, month DESC";

		        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
		            pstmt.setString(1, deptName);
		            ResultSet rs = pstmt.executeQuery();
		            
		            boolean found = false;
		            while (rs.next()) {
		                found = true;
		                System.out.printf("\n%d년 %d월\n", rs.getInt("year"), rs.getInt("month"));
		                System.out.printf("취업: %d명\n", rs.getInt("get_job"));
		                System.out.printf("진학: %d명\n", rs.getInt("school"));
		                System.out.printf("창업: %d명\n", rs.getInt("startup"));
		                System.out.printf("기타: %d명\n", rs.getInt("etc"));
		                System.out.println("------------------------");
		            }
		            
		            if (!found) {
		                System.out.println("해당 학과의 진로통계 정보가 없습니다.");
		            }
		        }
		    }

}
