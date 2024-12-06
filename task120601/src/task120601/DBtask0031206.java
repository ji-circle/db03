package task120601;

import java.sql.*;
import java.util.Scanner;

public class DBtask0031206 {

	// MySQL 연결 정보
	static final String DB_URL = "jdbc:mysql://localhost/causwtask1206?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC"; // 데이터베이스																													// 주소
	static final String USER = "root"; // MySQL 사용자 이름
	static final String PASS = "mysql"; // MySQL 비밀번호

	public static void main(String[] args) {
		try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
			Scanner scanner = new Scanner(System.in);

			while (true) {
				showMenu();

				int choice = scanner.nextInt();
				scanner.nextLine();

				switch (choice) {
				case 1:
					queryReferenceBooks(conn, scanner);
					break;
				case 2:
					borrowBook(conn, scanner);
					break;
				case 3:
					searchCareerStatistics(conn, scanner);
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

	//함수들
	//0. 처음 안내 멘트
	private static void showMenu() {
		System.out.println("\n====== 도서 관리 시스템 ======");
		System.out.println("===== 기능 메뉴 =====");
		System.out.println("1. 참고도서 조회 | 2. 도서 대출 | 3. 진료통계 조회 | 4. 종료 ");
		System.out.print("메뉴 선택: ");
	}
	
	// 1. 식별자를 입력받고, 그 수업의 담당교수별 참고도서 리스트를 검색하는 부분
	private static void queryReferenceBooks(Connection conn, Scanner scanner) throws SQLException {
		
		System.out.print("과목 ID(course_id): ");
		String courseId = scanner.nextLine();
		System.out.print("분반 ID(sec_id): ");
		String secId = scanner.nextLine();
		System.out.print("학기(semester): ");
		String semester = scanner.nextLine();
		System.out.print("년도(year): ");
		int year = scanner.nextInt();

		String sql = "SELECT rb.ISBN, rb.title, rb.author, rb.publisher, rb.year, i.name as instructor_name "
				+ "FROM reference_book rb " + "JOIN refers ref ON rb.ISBN = ref.ISBN "
				+ "JOIN teaches t ON ref.course_id = t.course_id " + "AND ref.sec_id = t.sec_id "
				+ "AND ref.semester = t.semester " + "AND ref.year = t.year " + "JOIN instructor i ON t.ID = i.ID "
				+ "WHERE ref.course_id = ? " + "AND ref.sec_id = ? " + "AND ref.semester = ? " + "AND ref.year = ?";

		//위에서 입력받은 것들 넣어서 쿼리 만들기
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
	
	//2. 책 빌리는 부분 - 학번, isbn 입력받음
	//  대기 부분은 제외이기에 borrow_queue에 넣는 부분은 제외함
	private static void borrowBook(Connection conn, Scanner scanner) throws SQLException {
	    System.out.print("학번: ");
	    String studentId = scanner.nextLine();
	    System.out.print("ISBN: ");
	    String isbn = scanner.nextLine();

	    // 수강생인지 확인 - 아니면 return함
	    String checkIfTaking = 
	        "SELECT t.course_id, t.sec_id, t.semester, t.year " +
	        "FROM takes t JOIN refers ref ON t.course_id = ref.course_id AND t.sec_id = ref.sec_id " + 
	        "AND t.semester = ref.semester AND t.year = ref.year " +
	        "WHERE t.ID = ? AND ref.ISBN = ?";

	    try (PreparedStatement pstmt = conn.prepareStatement(checkIfTaking)) {
	        pstmt.setString(1, studentId);
	        pstmt.setString(2, isbn);

	        ResultSet rs = pstmt.executeQuery();
	        if (!rs.next()) {
	            System.out.println("대출불허: 해당 도서의 수강생이 아닙니다.");
	            //리턴해버리기
	            return;
	        }

	        // 수강 정보 저장, insert할 때 이것 넣을것임
	        String courseId = rs.getString("course_id");
	        String secId = rs.getString("sec_id");
	        String semester = rs.getString("semester");
	        int year = rs.getInt("year");

	        // 학생 이름 조회해서 넣기
	        String getStudentNameSql = "SELECT name FROM student WHERE ID = ?";
	        try (PreparedStatement studentNameStmt = conn.prepareStatement(getStudentNameSql)) {
	            studentNameStmt.setString(1, studentId);
	            ResultSet nameRs = studentNameStmt.executeQuery();
	            if (nameRs.next()) {
	                String studentName = nameRs.getString("name");
	                
	                // 대출 정보 입력하는 부분
	                String borrowSql = 
	                    "INSERT INTO borrow_info (ISBN, ID, borrow_date, return_date, name, number, course_id, sec_id, semester, year) " +
	                    "VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)";

	                try (PreparedStatement borrowStmt = conn.prepareStatement(borrowSql)) {
	                    int borrowDate = Integer.parseInt(String.format("%tY%<tm%<td", new java.util.Date()));
	                    int returnDate = borrowDate + 7; // 7일 후 반납하니까 +7함

	                    borrowStmt.setString(1, isbn);
	                    borrowStmt.setString(2, studentId);
	                    borrowStmt.setInt(3, borrowDate);
	                    borrowStmt.setInt(4, returnDate);
	                    borrowStmt.setString(5, studentName);
	                    borrowStmt.setString(6, courseId);
	                    borrowStmt.setString(7, secId);
	                    borrowStmt.setString(8, semester);
	                    borrowStmt.setInt(9, year);

	                    int result = borrowStmt.executeUpdate();
	                    if (result > 0) {
	                        System.out.println("대출완료");
	                        System.out.println("대출일: " + borrowDate);
	                        System.out.println("반납예정일: " + returnDate);
	                    }
	                }
	            }
	        }
	    }
	}
	

	// 3. 학과별 진로통계를 조회하는 부분
	private static void searchCareerStatistics(Connection conn, Scanner scanner) throws SQLException {
		System.out.print("학과명: ");
		String deptName = scanner.nextLine();

		String sql = "SELECT year, month, get_job, school, startup, etc FROM career_statistics "
				+ "WHERE dept_name = ? ORDER BY year DESC, month DESC";

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
