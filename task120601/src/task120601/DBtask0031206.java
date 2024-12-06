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
	private static void borrowBook(Connection conn, Scanner scanner) throws SQLException {
	    System.out.print("학번: ");
	    String studentId = scanner.nextLine();
	    System.out.print("ISBN: ");
	    String isbn = scanner.nextLine();

	    // 1. 수강생 확인
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

	        // 2. 현재 도서의 대출 상태 확인
	        String checkCurrentBorrowSql = 
	            "SELECT MAX(return_date) as latest_return FROM borrow_info WHERE ISBN = ?";
	            
	        try (PreparedStatement checkStmt = conn.prepareStatement(checkCurrentBorrowSql)) {
	            checkStmt.setString(1, isbn);
	            ResultSet borrowRs = checkStmt.executeQuery();
	            
	            int borrowDate;
	            int returnDate;
	            
	            if (borrowRs.next() && borrowRs.getInt("latest_return") > Integer.parseInt(String.format("%tY%<tm%<td", new java.util.Date()))) {
	                // 이미 대출 중인 경우
	                borrowDate = borrowRs.getInt("latest_return");
	                returnDate = borrowDate + 7;
	                
	                // 대기열 번호 확인
	                String getQueueNumberSql = 
	                    "SELECT COALESCE(MAX(borr_id), 0) + 1 as next_id FROM borrow_queue WHERE ISBN = ?";
	                try (PreparedStatement queueStmt = conn.prepareStatement(getQueueNumberSql)) {
	                    queueStmt.setString(1, isbn);
	                    ResultSet queueRs = queueStmt.executeQuery();
	                    int queueNumber = queueRs.next() ? queueRs.getInt("next_id") : 1;

	                    // 먼저 borrow_info에 추가
	                    insertBorrowInfo(conn, isbn, studentId, borrowDate, returnDate);
	                    
	                    // 그 다음 borrow_queue에 추가
	                    String insertQueueSql = 
	                        "INSERT INTO borrow_queue (ISBN, ID, borrow_date, borr_id) VALUES (?, ?, ?, ?)";
	                    try (PreparedStatement insertQueueStmt = conn.prepareStatement(insertQueueSql)) {
	                        insertQueueStmt.setString(1, isbn);
	                        insertQueueStmt.setString(2, studentId);
	                        insertQueueStmt.setInt(3, borrowDate);
	                        insertQueueStmt.setInt(4, queueNumber);
	                        insertQueueStmt.executeUpdate();
	                        
	                        System.out.println("대출 대기 등록 완료");
	                        System.out.println("예약 순번: " + queueNumber);
	                        System.out.println("예상 대출가능일: " + borrowDate);
	                    }
	                }
	            } else {
	                // 대출 가능한 경우
	                borrowDate = Integer.parseInt(String.format("%tY%<tm%<td", new java.util.Date()));
	                returnDate = borrowDate + 7;
	                insertBorrowInfo(conn, isbn, studentId, borrowDate, returnDate);
	                System.out.println("대출완료");
	                System.out.println("대출일: " + borrowDate);
	                System.out.println("반납예정일: " + returnDate);
	            }
	        }
	    }
	}

	private static void insertBorrowInfo(Connection conn, String isbn, String studentId, 
	                                   int borrowDate, int returnDate) throws SQLException {
	    String borrowSql = 
	        "INSERT INTO borrow_info (ISBN, ID, borrow_date, return_date, name, number, course_id, sec_id, semester, year) " +
	        "SELECT ?, ?, ?, ?, s.name, NULL, r.course_id, r.sec_id, r.semester, r.year " +
	        "FROM student s, refers r " +
	        "WHERE s.ID = ? AND r.ISBN = ?";
	        
	    try (PreparedStatement pstmt = conn.prepareStatement(borrowSql)) {
	        pstmt.setString(1, isbn);
	        pstmt.setString(2, studentId);
	        pstmt.setInt(3, borrowDate);
	        pstmt.setInt(4, returnDate);
	        pstmt.setString(5, studentId);
	        pstmt.setString(6, isbn);
	        pstmt.executeUpdate();
	    }
	}

	// 반납 처리 및 대기열 업데이트를 위한 메소드
	private static void updateQueueAfterReturn(Connection conn, String isbn) throws SQLException {
	    // 대기열에서 첫 번째 대기자 삭제
	    String deleteFirstQueueSql = 
	        "DELETE FROM borrow_queue WHERE ISBN = ? AND borr_id = 1";
	        
	    // 나머지 대기자들의 borr_id 감소
	    String updateQueueSql = 
	        "UPDATE borrow_queue SET borr_id = borr_id - 1 " +
	        "WHERE ISBN = ? AND borr_id > 1";
	        
	    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteFirstQueueSql);
	         PreparedStatement updateStmt = conn.prepareStatement(updateQueueSql)) {
	        
	        deleteStmt.setString(1, isbn);
	        deleteStmt.executeUpdate();
	        
	        updateStmt.setString(1, isbn);
	        updateStmt.executeUpdate();
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
