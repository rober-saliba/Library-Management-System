
package control;

import java.io.File;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;

import control.EmailController;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import entity.Book;
import entity.Borrows;
import entity.ConstantsAndGlobalVars;
import entity.FaultsHistory;
import entity.History;
import entity.Librarian;
import entity.Message;
import entity.ManualDelays;
import entity.MsgParser;
import entity.MyFile;
import entity.History;
import entity.Reservations;
import entity.TableQueries;
import entity.TwoDaysMessage;
import entity.User;
import enums.BookCopyStatus;
import enums.BorrowStatus;
import enums.LogInStatus;
import enums.MessageType;
import enums.ReserveStatus;
import enums.UpdateCopyResult;
import enums.UserStatus;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Modality;

import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import entity.BookCopies;
import entity.Borrows;
import entity.ConstantsAndGlobalVars;
import entity.History;
import entity.MsgParser;
import entity.History;
import entity.Reservations;
import entity.User;
import enums.Result;
import enums.ExistStatus;
import enums.LogInStatus;
/**
 * The class responsible for connection and executing queries and updates to the schema.
 * all methods in this class are self documented.
 */
public class DBController {
	/**
	 * a Connection object to prepare and create statements.
	 */
	private static Connection conn;
	/**
	 * an arrayList that simulates the reservation queue.
	 */
	static ArrayList<TwoDaysMessage> timerList;
	/**
	 * connects to the SQL server.
	 * @see server.Server#openServerConnection(String, String, String, String).
	 * @return true if connection succeeded, false otherwise
	 */
public boolean connectToDB(String username, String password, String host, String dbName) {
    try {
        // NEW Driver for MySQL 8.x (already corrected)
        Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
        timerList = new ArrayList<>();
    } catch (Exception ex) {
        ex.printStackTrace();
    }

    // Updated Connection URL with Time Zone Fix
    String url = "jdbc:mysql://" + host + "/" + dbName + "?serverTimezone=Asia/Jerusalem";

    Statement s;
    try {
        conn = DriverManager.getConnection(url, username, password);
        System.out.println("SQL connection succeeded");
    } catch (SQLException ex) {
        System.out.println("SQLException: " + ex.getMessage());
        System.out.println("SQLState: " + ex.getSQLState());
        System.out.println("VendorError: " + ex.getErrorCode());
        return false;
    }
    return true;
}


	public void setConnection(Connection conn) {
	    this.conn = conn;
	}

	// gets the user if he/she is not already logged in.
	/**
	 * gets the user from the DB and checks the credentials sent, changes isLoggedIn status if all is valid.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser userLogin(MsgParser msg) {
		PreparedStatement stmt;
		User tmpUser = null;
		String username = ((User) msg.getCommPipe().get(0)).getUserID();
		String password = ((User) msg.getCommPipe().get(0)).getPassword();
		String table = msg.getTableName();
		try {

			ResultSet rs;
			if (table.equals("users")) {
				String loginQuery = "SELECT * FROM users U WHERE U.userID = ?";
				stmt = conn.prepareStatement(loginQuery);
				stmt.setString(1, username);
				rs = stmt.executeQuery();
			} else {
				String loginQuery = "SELECT U.* " + "FROM users U,librarians L "
						+ "WHERE  L.librarianID = U.userID AND L.librarianID = ?";
				stmt = conn.prepareStatement(loginQuery);
				stmt.setString(1, username);
				rs = stmt.executeQuery();
			}
			
			msg.setReturnResult(LogInStatus.Success);
			if (rs.next()) {
				int bit = Integer.parseInt(rs.getString(9));
				boolean isLoggedIn = (bit == 1) ? true : false;
				// check if already logged in
				if (isLoggedIn) {
					msg.setReturnResult(LogInStatus.isLoggedIn);
					return msg;
				}
				// check if password matches
				if (!rs.getString(6).equals(password)) {
					msg.setReturnResult(LogInStatus.WrongPassword);
					return msg;
				}
				tmpUser = new User();
				tmpUser.setUserID(rs.getString(1));
				tmpUser.setFirstName(rs.getString(2));
				tmpUser.setLastName(rs.getString(3));
				tmpUser.setPhoneNumber(rs.getString(4));
				tmpUser.setMembershipNumber(rs.getString(5));
				tmpUser.setPassword(rs.getString(6));
				tmpUser.setStatus(enums.UserStatus.valueOf(rs.getString(7)));
				tmpUser.setEmail(rs.getString(8));
				changeIsLoggedIn(username);

				msg.clearCommPipe();
				msg.addToCommPipe(tmpUser);
			} else {
				msg.setReturnResult(LogInStatus.UserNotExist);
				return msg;
			}
			
		} catch (SQLException e) {
//			msg.setReturnResult(LogInStatus.UserNotExist);
//			return msg;
		}

		return msg;
	}
	
	// if the all conditions to a log in are met, change isLoggedIn flag to 1.
	/**
	 * changes isLoggedIn status for the user specified.
	 * @param username - the user ID
	 */
	private void changeIsLoggedIn(String username) {
		PreparedStatement stmt;
		try {
			String isLoggedinQuery = "UPDATE users SET isLoggedIn = 1 WHERE userID = ?";
			stmt = conn.prepareStatement(isLoggedinQuery);
			stmt.setString(1, username);
			stmt.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * gets a list of books matching a search pattern and a search keyword.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser searchForBook(MsgParser msg) {
		PreparedStatement stmt, stmt1;
		ResultSet rs;
		String type = (String) msg.getCommPipe().get(0);
		String keyword = (String) msg.getCommPipe().get(1);
		msg.clearCommPipe();
		if (type.equals(ConstantsAndGlobalVars.searchType[0])) {// search by Book Title
			String bookTitleSearchQuery = "SELECT B.catalogNumber " + "FROM books B " + "WHERE (B.Title LIKE ?)";
			try {
				stmt = conn.prepareStatement(bookTitleSearchQuery);
				stmt.setString(1, "%" + keyword + "%");
				rs = stmt.executeQuery();
				while (rs.next()) {
					String CatalogNumber = rs.getString(1);
					MsgParser resultBook = new MsgParser<>();
					resultBook.addToCommPipe(CatalogNumber);
					resultBook = this.getBook(resultBook);
					Book b = (Book) resultBook.getCommPipe().get(0);
					msg.addToCommPipe(b);
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (type.equals(ConstantsAndGlobalVars.searchType[1])) {// search by Category
			String bookTitleSearchQuery = "SELECT B.catalogNumber " + "FROM books B, categories C "
					+ "WHERE (B.catalognumber = C.catalognumber AND C.categoryname LIKE ?)";
			try {
				stmt = conn.prepareStatement(bookTitleSearchQuery);
				stmt.setString(1, "%" + keyword + "%");
				rs = stmt.executeQuery();
				while (rs.next()) {
					String CatalogNumber = rs.getString(1);
					MsgParser resultBook = new MsgParser<>();
					resultBook.addToCommPipe(CatalogNumber);
					resultBook = this.getBook(resultBook);
					Book b = (Book) resultBook.getCommPipe().get(0);
					msg.addToCommPipe(b);
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (type.equals(ConstantsAndGlobalVars.searchType[2])) {// search by Description
			String bookTitleSearchQuery = "SELECT B.catalogNumber " + "FROM books B " + "WHERE (B.Description LIKE ?)";
			try {
				stmt = conn.prepareStatement(bookTitleSearchQuery);
				stmt.setString(1, "%" + keyword + "%");
				rs = stmt.executeQuery();
				while (rs.next()) {
					String CatalogNumber = rs.getString(1);
					MsgParser resultBook = new MsgParser<>();
					resultBook.addToCommPipe(CatalogNumber);
					resultBook = this.getBook(resultBook);
					Book b = (Book) resultBook.getCommPipe().get(0);
					msg.addToCommPipe(b);
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return msg;

	}
	
	
	public MsgParser getUserStatus(MsgParser msg) {
	    String userID = (String) msg.getCommPipe().get(0); // Extract the userID from the message
	    String query = "SELECT status FROM users WHERE userID = ?";
	    try (PreparedStatement stmt = conn.prepareStatement(query)) {
	        stmt.setString(1, userID);
	        ResultSet rs = stmt.executeQuery();
	        if (rs.next()) {
	            String status = rs.getString("status");
	            msg.clearCommPipe();
	            msg.addToCommPipe(status); // Add the retrieved status to the message
	        } else {
	            msg.clearCommPipe();
	            msg.addToCommPipe("User not found"); // Handle case where userID does not exist
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	    return msg;
	}

	
	

	/**
	 * gets the messages for a librarian
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser getMessages(MsgParser msg) {
		PreparedStatement stmt;
		String username = ((Message) msg.getCommPipe().get(0)).getBelong();
		ResultSet rs = null;
		String getMessagesQuery = "SELECT * FROM messages M WHERE M.belong = ?";
		msg.clearCommPipe();
		try {
			stmt = conn.prepareStatement(getMessagesQuery);
			stmt.setString(1, username);
			rs = stmt.executeQuery();
			while (rs.next()) {
				Message msgToAdd = new Message(enums.MessageType.valueOf(rs.getString(1)), rs.getString(2),
						rs.getString(3), rs.getString(4), rs.getDate(5), rs.getString(6));
				msg.addToCommPipe(msgToAdd);

			}

		} catch (SQLException e) {
		}
		return msg;
	}

	/**
	 * updates the settings for the user specified
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser userSettingUpdate(MsgParser msg) {
		PreparedStatement stmt;
		String username = ((User) msg.getCommPipe().get(0)).getUserID();
		String newPhoneNumber = ((User) msg.getCommPipe().get(0)).getPhoneNumber();
		String newMail = ((User) msg.getCommPipe().get(0)).getEmail();
		String updateSettingsQuery = "UPDATE users SET email =?, phoneNumber= ? WHERE userID = ?";
		msg.clearCommPipe();
		try {
			stmt = conn.prepareStatement(updateSettingsQuery);
			stmt.setString(1, newMail);
			stmt.setString(2, newPhoneNumber);
			stmt.setString(3, username);
			stmt.executeUpdate();
			msg.addToCommPipe("Success");
		} catch (SQLException e) {
			msg.addToCommPipe("Failed");
		}
		return msg;

	}

	/**
	 * updates the related tables upon returning a book.
	 * @param msg the parameters
	 * @return the return message
	 * @throws SQLException thrown if executing the update encounters a problem.
	 * @throws ParseException thrown if parsing the date encounters a problem
	 */
public MsgParser<String> returnBookUpdate(MsgParser msg) throws SQLException, ParseException {
    MsgParser<String> msg1 = msg;
    PreparedStatement stmt, stmt2, stmt3;
    ArrayList<String> bookBarcodes = new ArrayList<>();
    ArrayList<Reservations> bookReservations = new ArrayList<>();
    Statement stmt4;
    BorrowStatus bS;
    ResultSet rs = null;
    LocalDate returnDate, borrowDate;
    String userID, catalogNumber;
    String barcode = ((String) msg1.getCommPipe().get(0));
    LocalDate local = LocalDate.now();
    msg1.clearCommPipe();

    // Check if the book copy exists
    try {
        PreparedStatement firstStmt = conn.prepareStatement("SELECT * FROM bookcopies WHERE barcode = ?");
        firstStmt.setString(1, barcode);
        ResultSet firstRs = firstStmt.executeQuery();
        if (!firstRs.next()) {
            msg1.addToCommPipe("Barcode does not exist");
            return msg1;
        }
    } catch (SQLException e) {
        msg1.addToCommPipe("Barcode does not exist");
        return msg1;
    }

    // Get user ID and borrow details
    try {
        String getActiveBorrowQuery = "SELECT * FROM borrows WHERE barcode = ? AND (status = 'Active' OR status = 'LateNotReturned')";
        stmt = conn.prepareStatement(getActiveBorrowQuery);
        stmt.setString(1, barcode);
        rs = stmt.executeQuery();
    } catch (SQLException e) {
        msg1.addToCommPipe("No one has borrowed the book");
        return msg1;
    }

    if (rs.next()) {
        userID = rs.getString("userID");
        borrowDate = rs.getDate("borrowDate").toLocalDate();
        returnDate = rs.getDate("returnDate").toLocalDate();
    } else {
        msg1.addToCommPipe("No one has borrowed the book");
        return msg1;
    }

    // Determine borrow status (Returned or LateReturned)
    if (local.isAfter(returnDate)) {
        bS = BorrowStatus.LateReturned; // If the book is returned late
    } else {
        bS = BorrowStatus.Returned; // If the book is returned on time
    }

    try {
        // Update borrow record with actual return date and status
        String updateBorrowQuery = "UPDATE borrows SET actualReturnDate = NOW(), status = ? WHERE barcode = ? AND (status = 'Active' OR status = 'LateNotReturned')";
        stmt2 = conn.prepareStatement(updateBorrowQuery);
        stmt2.setString(1, bS.toString());
        stmt2.setString(2, barcode);
        stmt2.executeUpdate();

        // Update book copy status to available
        String updateCopyQuery = "UPDATE bookcopies SET status = 'available' WHERE barcode = ?";
        stmt3 = conn.prepareStatement(updateCopyQuery);
        stmt3.setString(1, barcode);
        stmt3.executeUpdate();
    } catch (SQLException e) {
        msg1.addToCommPipe("Failed to update borrow or book copy status");
        return msg1;
    }

    // Check if the user has other late returns
    if (bS == BorrowStatus.LateReturned) {
        try {
            PreparedStatement stmtLateReturns = conn.prepareStatement(
                "SELECT COUNT(*) FROM borrows WHERE userID = ? AND status = 'LateNotReturned'");
            stmtLateReturns.setString(1, userID);
            ResultSet rsLateReturns = stmtLateReturns.executeQuery();

            if (rsLateReturns.next() && rsLateReturns.getInt(1) > 0) {
                // User has other late returns, do not unfreeze
                msg1.addToCommPipe("User has other late returns and will remain frozen.");
            } else {
                // User returned all late books; defer unfreezing to punishment period
                msg1.addToCommPipe("All late books returned. User will remain frozen until the punishment period ends.");
            }
        } catch (SQLException e) {
            msg1.addToCommPipe("Error checking late returns");
            return msg1;
        }
    }

    // Handle reservations (unchanged logic)
    ResultSet barcodeN;
    PreparedStatement stmt7 = conn.prepareStatement("SELECT catalogNumber FROM bookcopies WHERE barcode = ?");
    stmt7.setString(1, barcode);
    barcodeN = stmt7.executeQuery();
    if (barcodeN.next()) {
        catalogNumber = barcodeN.getString(1);
    } else {
        msg1.addToCommPipe("Catalog does not exist");
        return msg1;
    }

    PreparedStatement stmt8 = conn.prepareStatement("SELECT barcode FROM bookcopies WHERE catalogNumber = ?");
    stmt8.setString(1, catalogNumber);
    barcodeN = stmt8.executeQuery();
    while (barcodeN.next()) {
        bookBarcodes.add(barcodeN.getString(1));
    }

    PreparedStatement stmt9 = conn.prepareStatement(
        "SELECT * FROM reservations WHERE reserveStatus = 'Pending' AND barcode = ?");
    for (String barcodeI : bookBarcodes) {
        stmt9.setString(1, barcodeI);
        ResultSet reserveN = stmt9.executeQuery();
        if (reserveN.next()) {
            bookReservations.add(new Reservations(reserveN.getString("userID"), reserveN.getString("barcode"),
                    reserveN.getTimestamp("reserveDate"), ReserveStatus.valueOf(reserveN.getString("reserveStatus"))));
        }
    }

    if (!bookReservations.isEmpty()) {
    	if (!bookReservations.isEmpty()) {
    	    Reservations earliestReservation = bookReservations.get(0);
    	    for (Reservations reservation : bookReservations) {
    	        LocalDateTime minDate = earliestReservation.gettS().toLocalDateTime();
    	        LocalDateTime currentDate = reservation.gettS().toLocalDateTime();
    	        if (minDate.isAfter(currentDate)) {
    	            earliestReservation = reservation;
    	        }
    	    }

    	    PreparedStatement stmtUpdateReservation = conn.prepareStatement(
    	        "UPDATE reservations SET reserveStatus = ? WHERE userID = ? AND barcode = ? AND reserveDate = ?");
    	    stmtUpdateReservation.setString(1, ReserveStatus.twoDaysPending.name());
    	    stmtUpdateReservation.setString(2, earliestReservation.getUserID());
    	    stmtUpdateReservation.setString(3, earliestReservation.getBarcode());
    	    stmtUpdateReservation.setTimestamp(4, earliestReservation.gettS());

    	    if (stmtUpdateReservation.executeUpdate() > 0) {
    	        System.out.println("Reservation status updated successfully for user ID: "
    	            + earliestReservation.getUserID());

    	        // Fetch email for the user
    	        PreparedStatement stmtFetchEmail = conn.prepareStatement(
    	            "SELECT email FROM users WHERE userID = ?");
    	        stmtFetchEmail.setString(1, earliestReservation.getUserID());
    	        ResultSet rsEmail = stmtFetchEmail.executeQuery();

    	        if (rsEmail.next()) {
    	            String recipientEmail = rsEmail.getString("email");
    	            String subject = "Book Arrived Notification";
    	            String body = "Dear User,\n\nYour reserved book is now available for pickup. "
    	                + "Please collect it within the next 2 days.\n\nBook Barcode: "
    	                + earliestReservation.getBarcode() + "\n\nThank you,\nBlib System";

    	            // Send email using EmailController
    	            EmailController.sendEmail(recipientEmail, subject, body);
    	            
 	            
    	            
    	        }
    	     
    	    }

    	    // Schedule a 48-hour timer for the reservation
    	    TwoDaysMessage twoDaysMessage = new TwoDaysMessage();
    	    Timer timer = new Timer();
    	    Calendar calendar = Calendar.getInstance();
    	    calendar.add(Calendar.HOUR, 48);
    	    Date expirationTime = calendar.getTime();

    	    timer.schedule(
    	        new TwoDaysMessagecontroller(this, earliestReservation, catalogNumber, barcode),
    	        expirationTime);

    	    twoDaysMessage.setTimer(timer);
    	    twoDaysMessage.setReservation(earliestReservation);
    	    twoDaysMessage.setRealBarcode(barcode);
    	    timerList.add(twoDaysMessage);
    	    
    	    

    	    
    	    
    	}
        msg1.addToCommPipe("Book returned successfully, reservation handled.");
    } else {
        msg1.addToCommPipe("Book returned successfully!");
    }

    return msg1;
}



	
//public MsgParser checkPendingReservations(MsgParser msg) {
//    String catalogNumber = (String) msg.getCommPipe().get(0);
//    msg.clearCommPipe();
//    try {
//        String query = "SELECT COUNT(*) " +
//                       "FROM reservations R " +
//                       "JOIN bookcopies BC ON R.barcode = BC.barcode " +
//                       "WHERE BC.catalogNumber = ? AND R.reserveStatus = 'Pending'";
//        PreparedStatement stmt = conn.prepareStatement(query);
//        stmt.setString(1, catalogNumber);
//        ResultSet rs = stmt.executeQuery();
//        if (rs.next()) {
//            msg.addToCommPipe(rs.getInt(1)); // Add count to CommPipe
//        } else {
//            msg.addToCommPipe(0); // No pending reservations
//        }
//    } catch (SQLException e) {
//        e.printStackTrace();
//        msg.addToCommPipe(-1); // Indicate failure
//    }
//    return msg;
//}

	
	
//public MsgParser updateBookTypeToRegular(MsgParser msg) {
//    String catalogNumber = (String) msg.getCommPipe().get(0);
//    msg.clearCommPipe();
//    try {
//        String query = "UPDATE books " +
//                       "SET type = 'Regular' " +
//                       "WHERE CatalogNumber = ? " +
//                       "AND NOT EXISTS (" +
//                       "    SELECT 1 " +
//                       "    FROM reservations R " +
//                       "    JOIN bookcopies BC ON R.barcode = BC.barcode " +
//                       "    WHERE BC.catalogNumber = ? AND R.reserveStatus = 'Pending'" +
//                       ")";
//        PreparedStatement stmt = conn.prepareStatement(query);
//        stmt.setString(1, catalogNumber);
//        stmt.setString(2, catalogNumber);
//        int rowsUpdated = stmt.executeUpdate();
//        if (rowsUpdated > 0) {
//            msg.addToCommPipe(enums.Result.Success); // Update successful
//        } else {
//            msg.addToCommPipe(enums.Result.Fail); // No rows updated
//        }
//    } catch (SQLException e) {
//        e.printStackTrace();
//        msg.addToCommPipe(enums.Result.Fail); // Indicate failure
//    }
//    return msg;
//}




	// function that delete reservation and send to the next one in the list i there
	// any
	/**
	 * updates the status of a reservation to 'Canceled'.
	 * @param R the reservation to delete
	 */
	public void deleteReservation(Reservations R) {
		PreparedStatement stmt;
		String bookCopyReserved = "UPDATE reservations SET reserveStatus= ? WHERE userID = ? AND barcode = ? AND reserveDate = ?";

		try {
			stmt = conn.prepareStatement(bookCopyReserved);
			stmt.setString(1, ReserveStatus.Canceled.name());
			stmt.setString(2, R.getUserID());
			stmt.setString(3, R.getBarcode());
			String s = R.gettS().toString().split("\\.")[0];
			stmt.setString(4, s);
			stmt.executeUpdate();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		// now send the copy to the next person

	}

	// check if there is another one want to reserve else change status of copy to
	// available
	public void sendMessageToNextOne(String catalogNumber, String barcode, String ID) {// --------------i'm working
																						// here----------------------------------------

		ArrayList<String> bookBarcodes = new ArrayList<>();
		ArrayList<Reservations> bookReservations = new ArrayList<>();
		// ----------------------------
		try {
			ResultSet barcodeN;
			PreparedStatement stmt11;
			String getAllBarcodes = "SELECT B.barcode FROM bookcopies B where B.catalogNumber= ? ";
			stmt11 = conn.prepareStatement(getAllBarcodes);
			stmt11.setString(1, catalogNumber);
			barcodeN = stmt11.executeQuery();
			while (barcodeN.next()) {
				bookBarcodes.add(barcodeN.getString(1));
			}
			if (bookBarcodes.isEmpty())
				return;

			PreparedStatement stmt12;
			ResultSet reserveN;
			String getAllBookReservations = "SELECT R.* FROM reservations R WHERE R.reserveStatus= 'Pending' AND R.barcode = ?";
			stmt12 = conn.prepareStatement(getAllBookReservations);

			int flag = 0;
			// get all reservations reserved that copy..
			for (String barcodeI : bookBarcodes) {
				stmt12.setString(1, barcodeI);
				reserveN = stmt12.executeQuery();
				if (reserveN.next()) {
					bookReservations.add(new Reservations(reserveN.getString(1), reserveN.getString(2),
							reserveN.getDate(3), ReserveStatus.valueOf(reserveN.getString(4))));
					flag++;
				}
			}

			if (flag > 0) // if there is reservations on that book get the earlier date.
			{
				Reservations earlierDateReserve = bookReservations.get(0);
				for (Reservations reservations : bookReservations) {
					LocalDate min = earlierDateReserve.getMysqlDate().toLocalDate();
					LocalDate next = reservations.getMysqlDate().toLocalDate();
					if (min.isAfter(next)) {
						earlierDateReserve = reservations;
					}

					PreparedStatement stmt123;
					String TwoDayMsg = "UPDATE reservations SET reserveStatus= ? WHERE userID = ? AND barcode = ? AND reserveDate=? ";
					stmt123 = conn.prepareStatement(TwoDayMsg);
					stmt123.setString(1, ReserveStatus.twoDaysPending.name());
					stmt123.setString(2, earlierDateReserve.getUserID());
					stmt123.setString(3, earlierDateReserve.getBarcode());
					stmt123.setDate(4, earlierDateReserve.getMysqlDate());
					stmt123.executeUpdate();

					System.out.println("your book has arrived you have 2 days to borrow it ID: "
							+ earlierDateReserve.getUserID() + " barcode: " + barcode);
					// send mail or add alert
					
					PreparedStatement stmtEmail;
					ResultSet mailRs = null;
					String getEmailQuery = "SELECT U.email FROM users U WHERE U.userID = ?";
					try {
						stmtEmail = conn.prepareStatement(getEmailQuery);
						stmtEmail.setString(1, earlierDateReserve.getUserID());
						mailRs = stmtEmail.executeQuery();
						if (mailRs.next()) {
							String toSendMail = mailRs.getString(1);
						    EmailController.sendEmail(
						            toSendMail,
						            "Book Arrived",
						            "Your book has arrived. You have 2 days to borrow it. ID: "
						            + earlierDateReserve.getUserID()
						            + ", Barcode: " + barcode
						        );
						}

					} catch (SQLException e) {
					}

					TwoDaysMessage TDM = new TwoDaysMessage();
					Timer timer = new Timer();
					Date currentDate = new Date();
					Calendar c = Calendar.getInstance();
					c.setTime(currentDate);
					c.add(Calendar.HOUR, 48);
					// c.add(Calendar.SECOND, 30);
					// convert calendar to date
					Date currentDatePlus48H = c.getTime();
					timer.schedule(new TwoDaysMessagecontroller(this, earlierDateReserve, catalogNumber, barcode),
							currentDatePlus48H);
					TDM.setTimer(timer);
					TDM.setReservation(earlierDateReserve);
					timerList.add(TDM);
					for (TwoDaysMessage twoDaysMessage : timerList) {
						if (twoDaysMessage.getReservation().getUserID().equals(ID)
								&& twoDaysMessage.getRealBarcode().equals(barcode)) {
							timerList.remove(twoDaysMessage);
						}
					}
				}
			} else {
				// delete the reservation from ArrayList<TwoDaysMessage>
				for (TwoDaysMessage twoDaysMessage : timerList) {
					if (twoDaysMessage.getReservation().getUserID().equals(ID)
							&& twoDaysMessage.getRealBarcode().equals(barcode)) {
						timerList.remove(twoDaysMessage);
					}
				}

			}
			flag = 0;

		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

	/**
	 * sends a return notice to all users who borrowed a book.
	 */
public void checkDayBeforeReturnAndSendMessage() {
    PreparedStatement stmt;
    ResultSet rs;
    String getActiveBorrows = "SELECT B.* FROM borrows B WHERE B.status = 'Active'";
    LocalDate currentDate1 = LocalDate.now();
    LocalDate borrowDate, borrowDateMinusOneDay;
    String ID, barcode;

    try {
        stmt = conn.prepareStatement(getActiveBorrows);
        rs = stmt.executeQuery();
        while (rs.next()) {
            borrowDate = rs.getDate(5).toLocalDate();
            borrowDateMinusOneDay = borrowDate.minusDays(1);
            if (borrowDateMinusOneDay.isEqual(currentDate1)) {
                ID = rs.getString(1);
                barcode = rs.getString(2);
                System.out.println("user id: " + ID + " you have one day to return book barcode number: " + barcode);

                PreparedStatement stmtEmail;
                ResultSet mailRs = null;
                String getEmailQuery = "SELECT U.email FROM users U WHERE U.userID = ?";
                try {
                    stmtEmail = conn.prepareStatement(getEmailQuery);
                    stmtEmail.setString(1, ID);
                    mailRs = stmtEmail.executeQuery();
                    if (mailRs.next()) {
                        String toSendMail = mailRs.getString(1);
                        EmailController.sendEmail(
                            toSendMail,
                            "Warning Message!",
                            "User ID: " + ID + " you have one day to return the book. Barcode number: " + barcode
                        );
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
}

	/**
	 * checks if any user has late returns and freezes the account
	 * @throws ParseException thrown if parsing the date encounters a problem
	 */
public void checkLateReturn() {
    PreparedStatement stmtLateBorrows, stmtFreezeUser, stmtAddFault, stmtUpdateBorrow;
    ResultSet rsLateBorrows;

    try {
        // 1. Query for late borrows past the 7-day grace period
        String queryLateBorrows = "SELECT B.userID, B.barcode, B.returnDate " +
                                  "FROM borrows B " +
                                  "WHERE B.status = 'Active' AND B.returnDate < DATE_SUB(NOW(), INTERVAL 7 DAY)";
        stmtLateBorrows = conn.prepareStatement(queryLateBorrows);
        rsLateBorrows = stmtLateBorrows.executeQuery();

        while (rsLateBorrows.next()) {
            String userID = rsLateBorrows.getString("userID");
            String barcode = rsLateBorrows.getString("barcode");

            // Freeze the user and log a fault
            // 2. Update the user status to 'Frozen'
            String freezeUserQuery = "UPDATE users SET status = 'Frozen' WHERE userID = ?";
            stmtFreezeUser = conn.prepareStatement(freezeUserQuery);
            stmtFreezeUser.setString(1, userID);
            stmtFreezeUser.executeUpdate();

            // 3. Log the fault in faultshistory table
            String addFaultQuery = "INSERT INTO faultshistory (userID, faultDesc, Date) VALUES (?, 'Not Resolved', NOW())";
            stmtAddFault = conn.prepareStatement(addFaultQuery);
            stmtAddFault.setString(1, userID);
            stmtAddFault.executeUpdate();

            // 4. Update the borrow status to 'LateNotReturned' (if not already updated)
            String updateBorrowStatusQuery = "UPDATE borrows SET status = 'LateNotReturned' WHERE barcode = ? AND status = 'Active'";
            stmtUpdateBorrow = conn.prepareStatement(updateBorrowStatusQuery);
            stmtUpdateBorrow.setString(1, barcode);
            stmtUpdateBorrow.executeUpdate();

            System.out.println("User " + userID + " has been frozen due to overdue book with barcode: " + barcode);
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
}


public void checkPenalty() {
    PreparedStatement stmtFrozenUsers, stmtFaultDate, stmtUnfreeze, stmtCheckLateBorrows, stmtUpdateFaultDate;
    ResultSet rsFrozenUsers, rsFaultDate, rsLateBorrows;

    try {
        // 1. Get all users with 'Frozen' status
        String queryFrozenUsers = "SELECT userID FROM users WHERE status = 'Frozen'";
        stmtFrozenUsers = conn.prepareStatement(queryFrozenUsers);
        rsFrozenUsers = stmtFrozenUsers.executeQuery();

        while (rsFrozenUsers.next()) {
            String userID = rsFrozenUsers.getString("userID");

            // 2. Get the latest fault date for the user
            String queryLatestFault = "SELECT MAX(Date) AS latestFaultDate FROM faultshistory WHERE userID = ?";
            stmtFaultDate = conn.prepareStatement(queryLatestFault);
            stmtFaultDate.setString(1, userID);
            rsFaultDate = stmtFaultDate.executeQuery();

            if (rsFaultDate.next() && rsFaultDate.getDate("latestFaultDate") != null) {
                LocalDate latestFaultDate = rsFaultDate.getDate("latestFaultDate").toLocalDate();
                LocalDate currentDate = LocalDate.now();

                // Check if 30 days have passed since the latest fault
                if (currentDate.isAfter(latestFaultDate.plusDays(30))) {
                    // 3. Check if user has any overdue books
                    String queryLateBorrows = "SELECT COUNT(*) AS lateCount " +
                                              "FROM borrows " +
                                              "WHERE userID = ? AND status = 'LateNotReturned'";
                    stmtCheckLateBorrows = conn.prepareStatement(queryLateBorrows);
                    stmtCheckLateBorrows.setString(1, userID);
                    rsLateBorrows = stmtCheckLateBorrows.executeQuery();

                    if (rsLateBorrows.next()) {
                        int lateCount = rsLateBorrows.getInt("lateCount");

                        if (lateCount == 0) {
                            // No overdue books, unfreeze the user
                            String queryUnfreeze = "UPDATE users SET status = 'Active' WHERE userID = ?";
                            stmtUnfreeze = conn.prepareStatement(queryUnfreeze);
                            stmtUnfreeze.setString(1, userID);
                            stmtUnfreeze.executeUpdate();

                            // Update the fault description to 'Resolved' for the latest fault
                            String queryUpdateFaultDesc = "UPDATE faultshistory SET faultDesc = 'Resolved' WHERE userID = ? AND Date = ?";
                            stmtUpdateFaultDate = conn.prepareStatement(queryUpdateFaultDesc);
                            stmtUpdateFaultDate.setString(1, userID);
                            stmtUpdateFaultDate.setDate(2, java.sql.Date.valueOf(latestFaultDate));
                            stmtUpdateFaultDate.executeUpdate();

                            System.out.println("User " + userID + " has been unfrozen after serving their penalty.");
                        } else {
                            // User still has overdue books; extend the penalty period
                            String queryExtendPenalty = "UPDATE faultshistory SET Date = NOW() WHERE userID = ? AND Date = ?";
                            stmtUpdateFaultDate = conn.prepareStatement(queryExtendPenalty);
                            stmtUpdateFaultDate.setString(1, userID);
                            stmtUpdateFaultDate.setDate(2, java.sql.Date.valueOf(latestFaultDate));
                            stmtUpdateFaultDate.executeUpdate();

                            System.out.println("Penalty period extended for user " + userID + " due to overdue books.");
                        }
                    }
                } else {
                    System.out.println("User " + userID + " is still within the penalty period.");
                }
            } else {
                System.out.println("No fault history found for user: " + userID);
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
}

	/**
	 * gets number of messages for a specific librarian
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser getNumberMsg(MsgParser msg) {
		PreparedStatement stmt;
		ResultSet rs = null;
		String username = (String) msg.getCommPipe().get(0);
		String numberOfMsg = "SELECT COUNT(*) FROM messages M WHERE M.belong = ?";
		msg.clearCommPipe();
		try {
			stmt = conn.prepareStatement(numberOfMsg);
			stmt.setString(1, username);
			rs = stmt.executeQuery();
			if (rs.next())
				msg.addToCommPipe(Integer.toString(rs.getInt(1)));
			else
				msg.addToCommPipe("0");
		} catch (SQLException e) {
			msg.addToCommPipe("0");
		}
		return msg;
	}

	/**
	 * changes the isLoggedIn status of a user
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser userLogout(MsgParser msg) {
		PreparedStatement stmt;
		String username = ((User) msg.getCommPipe().get(0)).getUserID();
		String logoutQuery = "UPDATE users SET isLoggedIn = 0 WHERE userID = ?";
		msg.clearCommPipe();
		try {
			stmt = conn.prepareStatement(logoutQuery);
			stmt.setString(1, username);
			stmt.executeUpdate();
			msg.addToCommPipe("Success");
		} catch (SQLException e) {
			msg.addToCommPipe("Failed");
		}
		return msg;
	}

	/**
	 * gets all active borrows for a user
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser userBorrow(MsgParser msg) {
		PreparedStatement stmt;
		Borrows tmpBorrows;
		String username = ((User) msg.getCommPipe().get(0)).getUserID();
		String getActiveBorrowsQuery = "SELECT * " + "FROM borrows B "
				+ "WHERE B.userID = ? AND (B.status = 'Active' OR B.status = 'LateNotReturned')";
		try {
			stmt = conn.prepareStatement(getActiveBorrowsQuery);
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			// get the matching tuple, if there's any
			msg.clearCommPipe();
			while (rs.next()) {
				
				tmpBorrows = new Borrows();
				System.out.println(rs.getString(1));
				tmpBorrows.setUserID(rs.getString(1));
				System.out.println(rs.getString(2));
				tmpBorrows.setBarcode(rs.getString(2));
				System.out.println(rs.getString(3));
				tmpBorrows.setLibrarianID(rs.getString(3));
				System.out.println(rs.getDate(4));
				tmpBorrows.setBorrowDate(rs.getDate(4));
				System.out.println(rs.getDate(5));
				tmpBorrows.setReturnDate(rs.getDate(5));
				System.out.println(rs.getDate(6));
				tmpBorrows.setActualReturnDate(rs.getDate(6));
				System.out.println(rs.getString(7));
				tmpBorrows.setStatus(enums.BorrowStatus.valueOf(rs.getString(7)));
				msg.addToCommPipe(tmpBorrows);
			}

		} catch (SQLException e) {
			System.out.println("Error");
			e.printStackTrace();
		}
		return msg;
	}

	/**
	 * gets all pending reservations for a user.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser userReservations(MsgParser msg) {
		PreparedStatement stmt;
		Reservations tmpReservations;
		String username = ((User) msg.getCommPipe().get(0)).getUserID();
		String getPendingReservationsQuery = "SELECT * " + "FROM reservations R "
				+ "WHERE (R.userID = ? AND (R.reserveStatus = 'Pending' OR reserveStatus= 'twoDaysPending'))";
		try {
			stmt = conn.prepareStatement(getPendingReservationsQuery);
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			// get the matching tuple, if there's any
			msg.clearCommPipe();
			while (rs.next()) {
				tmpReservations = new Reservations();
				tmpReservations.setUserID(rs.getString(1));
				tmpReservations.setBarcode(rs.getString(2));
				tmpReservations.setReserveDate(rs.getDate(3));
				tmpReservations.setStatus(enums.ReserveStatus.valueOf(rs.getString(4)));
				msg.addToCommPipe(tmpReservations);
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return msg;
	}

	/**
	 * gets all inactive borrows and reservations for a user
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser userHistory(MsgParser msg) {
		PreparedStatement stmt;
		History tmpHistory;
		Reservations tmpReservations;
		Borrows tmpBorrows;
		String username = ((User) msg.getCommPipe().get(0)).getUserID();
		try {
			String getHistoryQuery = "SELECT * " + "FROM borrows B "
					+ "WHERE B.userID = ? AND B.status != 'Active' AND B.status !='LateNotReturned'";
			stmt = conn.prepareStatement(getHistoryQuery);
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			// get the matching tuples, if there's any
			msg.clearCommPipe();
			while (rs.next()) {
				tmpHistory = new History();
				tmpHistory.setUserID(rs.getString(1));
				tmpHistory.setBarcode(rs.getString(2));
				tmpHistory.setType(enums.EventType.Borrow);
				tmpHistory.setEventDate(rs.getDate(4));
				msg.addToCommPipe(tmpHistory);
			}
			getHistoryQuery = "SELECT * " + "FROM reservations R "
					+ "WHERE R.userID = ? AND R.reserveStatus != 'Pending' AND R.reserveStatus != 'twoDaysPending'";
			stmt = conn.prepareStatement(getHistoryQuery);
			stmt.setString(1, username);
			ResultSet rs1 = stmt.executeQuery();
			while (rs1.next()) {
				tmpHistory = new History();
				tmpHistory.setUserID(rs1.getString(1));
				tmpHistory.setBarcode(rs1.getString(2));
				tmpHistory.setType(enums.EventType.Reserve);
				tmpHistory.setEventDate(rs1.getDate(3));
				msg.addToCommPipe(tmpHistory);
			}

		} catch (SQLException e) {

		}
		return msg;
	}

	/**
	 * gets total number of books in the inventory.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser numberOfBooks(MsgParser mp) {
		Statement stmt;
		ResultSet rs;
		int totalNumberOfBooks = 0;
		mp.clearCommPipe();
		try {
			// no need for PreparedStatement, query isn't parameterised
			stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT COUNT(*) FROM books B");
			if (rs.next())
				totalNumberOfBooks = rs.getInt(1);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		mp.addToCommPipe(totalNumberOfBooks);
		return mp;
	}

	/**
	 * adds a new user tuple to the users table
	 * @param msg the parameters
	 * @return the return message
	 */
public MsgParser addNewUser(MsgParser mp) {
    PreparedStatement stmt;
    User newUser = (User) mp.getCommPipe().get(0);
    mp.clearCommPipe();
    ResultSet rs;
    try {
        String getUserQuery = "SELECT U.userID FROM users U WHERE U.userID = ?";
        stmt = conn.prepareStatement(getUserQuery);
        stmt.setString(1, newUser.getUserID());
        rs = stmt.executeQuery();
        System.out.println("Checking if user exists: " + newUser.getUserID());
        if (rs.next()) {
            System.out.println("User already exists: " + newUser.getUserID());
            mp.addToCommPipe(false); // User already exists
        } else {
            System.out.println("Adding new user: " + newUser.getUserID());
            String addTupleQuery = "INSERT INTO users VALUES (?,?,?,?,?,?,'Active',?,0)";
            stmt = conn.prepareStatement(addTupleQuery);
            stmt.setString(1, newUser.getUserID());
            stmt.setString(2, newUser.getFirstName());
            stmt.setString(3, newUser.getLastName());
            stmt.setString(4, newUser.getPhoneNumber());
            stmt.setString(5, newUser.getMembershipNumber());
            stmt.setString(6, newUser.getPassword());
            stmt.setString(7, newUser.getEmail());
            if (stmt.executeUpdate() > 0) {
                System.out.println("User added successfully: " + newUser.getUserID());
                mp.addToCommPipe(true); // User added successfully
            } else {
                System.out.println("Failed to add user: " + newUser.getUserID());
                mp.addToCommPipe(false); // Insert failed
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
        mp.addToCommPipe(false); // Database error
    }

    return mp;
}

	/**
	 * adds a new book tuple to the books table.
	 * @param msg the parameters
	 * @return the return message
	 * @throws ParseException thrown if parsing the date encounters a problem
	 */
	public MsgParser addBook(MsgParser mp) throws ParseException {
		Book b = (Book) mp.getCommPipe().get(0);
		String insertBookQuery = "INSERT INTO books values (?,?,?,?,?,?,?,?,?)";
		mp.clearCommPipe();
		/*
		 * Convert Java Date to SQL Date
		 */
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		Date input = b.getPurchaseDate();
		String pd = df.format(input);
		Date temp = new SimpleDateFormat("yyyy-MM-dd").parse(pd);
		// java.sql.Date d = new java.sql.Date(temp.getDate());
		java.sql.Date d = new java.sql.Date(temp.getTime());

		try {
			PreparedStatement stmt = conn.prepareStatement(insertBookQuery);
			stmt.setString(1, b.getCatalogNumber());
			stmt.setString(2, b.getTitle());
			stmt.setString(3, b.getAuthorName());
			stmt.setString(4, b.getPublication());
			stmt.setInt(5, b.getNumberOfCopies());
			stmt.setDate(6, d);// convert to SQL Date
			stmt.setString(7, b.getLocationOnShelf());
			stmt.setString(8, b.getDescription());
			stmt.setString(9, b.getType().name());
			if (stmt.executeUpdate() > 0) {
				stmt.close();
				String addCategoryQuery = "INSERT INTO categories VALUES(?,?)";
				ArrayList<String> categoriesArr = b.getCategories();
				for (String string : categoriesArr) {
					stmt = conn.prepareStatement(addCategoryQuery);
					stmt.setString(1, string);
					stmt.setString(2, b.getCatalogNumber());
					int res = stmt.executeUpdate();
					stmt.close();
					if (res == 0) {
						// catalogNumber is a PFK in categories set to ON-DELETE:CASCADE..
						String abortQuery = "DELETE FROM books WHERE (catalogNumber = ?)";
						stmt = conn.prepareStatement(abortQuery);
						stmt.setString(1, b.getCatalogNumber());
						stmt.executeUpdate();
						mp.addToCommPipe(false);
						return mp;
					}
				}
				mp.addToCommPipe(true);
			} else
				mp.addToCommPipe(false);
		} catch (SQLException e) {
			e.printStackTrace();
			mp.addToCommPipe(false);
		}
		return mp;
	}

	/**
	 * removes a book tuple from the books table.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser removeBook(MsgParser mp) {
		PreparedStatement stmt;
		String catalogNumber = (String) mp.getCommPipe().get(0);
		mp.clearCommPipe();
		try {
			String removeBookQuery = "DELETE FROM books WHERE (CatalogNumber = ?)";
			stmt = conn.prepareStatement(removeBookQuery);
			stmt.setString(1, catalogNumber);
			if (stmt.executeUpdate() > 0)
				mp.addToCommPipe(true);
			else
				mp.addToCommPipe(false);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			mp.addToCommPipe(false);
		}
		return mp;
	}

	/**
	 * searched for a user and returns an object containing the user details.
	 * @param msg the parameters
	 * @return the return message
	 * @throws SQLException thrown should executing the query encounter a problem
	 */
	public MsgParser searchForUser(MsgParser mp) throws SQLException {
		String getUserQuery = "SELECT * FROM users U WHERE U.userID = ?";
		PreparedStatement prStmt = conn.prepareStatement(getUserQuery);
		prStmt.setString(1, ((User) mp.getCommPipe().get(0)).getUserID());

		ResultSet rs = prStmt.executeQuery();
		mp.clearCommPipe();
		User tmpUser = new User();

		if (rs.next()) {
			tmpUser.setUserID(rs.getString(1));
			tmpUser.setFirstName(rs.getString(2));
			tmpUser.setLastName(rs.getString(3));
			tmpUser.setPhoneNumber(rs.getString(4));
			tmpUser.setMembershipNumber(rs.getString(5));
			tmpUser.setPassword(rs.getString(6));
			tmpUser.setStatus(enums.UserStatus.valueOf(rs.getString(7)));
			tmpUser.setEmail(rs.getString(8));
		} else {
			tmpUser = null;
		}
		mp.addToCommPipe(tmpUser);
		return mp;
	}

	
	public MsgParser checkNumOfCopy(MsgParser msg) {
		PreparedStatement stmt;
		int numBorrowCopy;
		String barcode = ((BookCopies) msg.getCommPipe().get(0)).getBarcode();
		try {
			String checkNumOfCopyQuery = "SELECT COUNT(*) " + "FROM borrows B " + "WHERE B.barcode = ?";
			stmt = conn.prepareStatement(checkNumOfCopyQuery);
			stmt.setString(1, barcode);
			ResultSet rs = stmt.executeQuery();
			// get the matching tuple, if there's any
			msg.clearCommPipe();
			if (rs.next()) {
				numBorrowCopy = rs.getInt(1);
				msg.setNumOfBorrowCopies(numBorrowCopy);

			} else {
				return msg;
			}

		} catch (SQLException e) {
//			msg.setReturnResult(LogInStatus.UserNotExist);
//			return msg;
		}
		return msg;

	}

	/**
	 * @see control.DBController#searchForUser(MsgParser)
	 * @param msg the parameters
	 * @return the return message
	 */
public MsgParser checkUser(MsgParser msg) {
    PreparedStatement stmt;
    User tmpUser = null;
    String username = ((User) msg.getCommPipe().get(0)).getUserID();
    try {
        // Query to fetch user details
        String getUserQuery = "SELECT userID, firstName, lastName, phoneNumber, membershipNumber, password, status, email " +
                              "FROM users WHERE userID = ?";
        stmt = conn.prepareStatement(getUserQuery);
        stmt.setString(1, username);
        ResultSet rs = stmt.executeQuery();

        // Clear previous data in MsgParser
        msg.clearCommPipe();

        // If a user is found, populate the User object
        if (rs.next()) {
            tmpUser = new User();
            tmpUser.setUserID(rs.getString("userID"));
            tmpUser.setFirstName(rs.getString("firstName"));
            tmpUser.setLastName(rs.getString("lastName"));
            tmpUser.setPhoneNumber(rs.getString("phoneNumber"));
            tmpUser.setMembershipNumber(rs.getString("membershipNumber"));
            tmpUser.setPassword(rs.getString("password"));
            tmpUser.setStatus(enums.UserStatus.valueOf(rs.getString("status"))); // Convert status to enum
            tmpUser.setEmail(rs.getString("email"));
            msg.addToCommPipe(tmpUser); // Add the User object to the MsgParser
            msg.setIsExist(ExistStatus.Exist); // Mark user as existing
        } else {
            msg.addToCommPipe(null); // No user found
            msg.setIsExist(ExistStatus.NotExist); // Mark user as not existing
        }
    } catch (SQLException e) {
        e.printStackTrace();
        msg.addToCommPipe(null); // Handle exception gracefully
        msg.setIsExist(ExistStatus.NotExist);
    }

    return msg; // Return the MsgParser
}

	public MsgParser checkCopy(MsgParser msg) {
		PreparedStatement stmt;
		BookCopies tmpCopy = null;
		Book tmpBook = null;
		String barcode = ((BookCopies) msg.getCommPipe().get(0)).getBarcode();
		try {
			String checkCopyQuery = "SELECT * " + "FROM bookcopies C " + "WHERE C.barcode = ?";
			stmt = conn.prepareStatement(checkCopyQuery);
			stmt.setString(1, barcode);
			ResultSet rs = stmt.executeQuery();
			// get the matching tuple, if there's any
			msg.clearCommPipe();
			if (rs.next()) {
				tmpCopy = new BookCopies();
				tmpCopy.setBarcode(rs.getString(1));
				tmpCopy.setCatalogNumber(rs.getString(2));
				tmpCopy.setPurchaseDate(rs.getDate(3));
				tmpCopy.setStatus(enums.BookCopyStatus.valueOf(rs.getString(4)));
				msg.addToCommPipe(tmpCopy);

			} else {
				msg.addToCommPipe(tmpCopy);
				return msg;
			}

		} catch (SQLException e) {
			e.printStackTrace();
			msg.addToCommPipe(tmpCopy);

		}
		return msg;

	}

	/**
	 * gets the book type and number of copies for a specific book.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser checkBookType(MsgParser msg) {
		String catalog = ((BookCopies) msg.getCommPipe().get(0)).getCatalogNumber();
		PreparedStatement stmt;
		BookCopies tmpCopy = null;
		Book tmpBook = null;
		enums.BookType type;
		int numOfCopies;
		try {
			String checkDateQuery = "SELECT B.numberOfCopies,B.type " + "FROM books B " + "WHERE B.CatalogNumber = ?";
			stmt = conn.prepareStatement(checkDateQuery);
			stmt.setString(1, catalog);
			ResultSet rs = stmt.executeQuery();
			msg.clearCommPipe();
			if (rs.next()) {
				numOfCopies = rs.getInt(1);
				type = enums.BookType.valueOf(rs.getString(2));
				msg.setNumOfCopies(numOfCopies);
				msg.setType(type);
			} else {
				System.out.println("Error checkbook");
			}

		} catch (SQLException e) {
			e.printStackTrace();

		}
		return msg;

	}

	/**
	 * inserts a new borrow tuple into the borrows table.
	 * @param msg the parameters
	 * @return the return message
	 * @throws ParseException thrown should parsing the date encounter problems.
	 */
	public MsgParser updateBorrowTable(MsgParser msg) throws ParseException {
		PreparedStatement stmt;
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date borrowDate = ((Borrows) msg.getCommPipe().get(0)).getBorrowDate();
		Date returnDate = ((Borrows) msg.getCommPipe().get(0)).getReturnDate();
		Date actualReturnDate = ((Borrows) msg.getCommPipe().get(0)).getActualReturnDate();
		String retDate = df.format(returnDate);
		String currentDate = df.format(borrowDate);
		Date utilDate1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(currentDate);
		Date utilDate2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(retDate);
		java.sql.Date sqlDate1 = new java.sql.Date(utilDate1.getTime());
		java.sql.Date sqlDate2 = new java.sql.Date(utilDate2.getTime());
		try {
			stmt = conn.prepareStatement("INSERT INTO borrows VALUES(?,?,?,NOW(),?,?,?)");
			stmt.setString(1, ((Borrows) msg.getCommPipe().get(0)).getUserID());
			stmt.setString(2, ((Borrows) msg.getCommPipe().get(0)).getBarcode());
			stmt.setString(3, ((Borrows) msg.getCommPipe().get(0)).getLibrarianID());
			// stmt.setDate(4, sqlDate1);
			stmt.setDate(4, sqlDate2);
			stmt.setDate(5, null);
			stmt.setString(6, enums.BorrowStatus.Active.name());
			stmt.executeUpdate();
			msg.clearCommPipe();
			msg.setBorrowresult(Result.Success);

		} catch (SQLException e) {
			msg.setBorrowresult(Result.Fail);
		}
		return msg;

	}

	/**
	 * updates an existing book tuple.
	 * @param msg the parameters
	 * @return the return message
	 * @throws ParseException thrown should parsing the date encounter problems.
	 */
	public MsgParser updateBook(MsgParser mp) throws ParseException {
		Book b = (Book) mp.getCommPipe().get(0);
		String updateBookQuery = "UPDATE books B SET B.Title = ?, B.AuthorName = ?, B.Publication = ?, B.numberOfCopies = ?, B.purchaseDate = ?, "
				+ "B.locationOnShelf = ?, B.tableOfContents = ?, B.Description = ?, B.type = ? WHERE (B.catalogNumber = ?)";
		mp.clearCommPipe();
		/*
		 * Convert Java Date to SQL Date
		 */
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		Date input = b.getPurchaseDate();
		String pd = df.format(input);
		Date temp = new SimpleDateFormat("yyyy-MM-dd").parse(pd);
		// java.sql.Date d = new java.sql.Date(temp.getDate());
		java.sql.Date purchaseDate = new java.sql.Date(temp.getTime());

		try {
			PreparedStatement stmt = conn.prepareStatement(updateBookQuery);
			stmt.setString(1, b.getTitle());
			stmt.setString(2, b.getAuthorName());
			stmt.setString(3, b.getPublication());
			stmt.setInt(4, b.getNumberOfCopies());
			stmt.setDate(5, purchaseDate);// convert to SQL Date
			stmt.setString(6, b.getLocationOnShelf());
			stmt.setString(7, b.getDescription());
			stmt.setString(8, b.getType().name());
			stmt.setString(9, b.getCatalogNumber());
			stmt.executeUpdate();
			if (stmt.executeUpdate() > 0)
				mp.addToCommPipe(true);
			else
				mp.addToCommPipe(false);
		} catch (SQLException e) {
			e.printStackTrace();
			mp.addToCommPipe(false);
		}
		return mp;
	}

	/**
	 * searches for a book and returns it.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser getBook(MsgParser mp) {
		PreparedStatement stmt;
		ResultSet rs;
		String catalogNumber = (String) mp.getCommPipe().get(0);
		String getBookQuery = "SELECT B.* FROM books B WHERE B.catalogNumber = ? ";
		String getCategoriesQuery = "SELECT C.categoryName FROM categories C WHERE C.catalogNumber = ? ";
		mp.clearCommPipe();
		Book b = null;
		try {
			stmt = conn.prepareStatement(getBookQuery);
			stmt.setString(1, catalogNumber);
			System.out.println(b);
			rs = stmt.executeQuery();
			if (rs.next()) {
			    b = new Book();
			    b.setCatalogNumber(rs.getString(1)); // catalogNumber
			    b.setTitle(rs.getString(2));         // title
			    b.setAuthorName(rs.getString(3));    // authorName
			    b.setPublication(rs.getString(4));  // publication
			    b.setNumberOfCopies(rs.getInt(5));  // numberOfCopies

			    java.sql.Date date = rs.getDate(6); // purchaseDate
			    Date purchaseDate = (date != null) ? new Date(date.getTime()) : null;
			    b.setPurchaseDate(purchaseDate);

			    b.setLocationOnShelf(rs.getString(7)); // locationOnShelf

			    // Map description
			    b.setDescription(rs.getString(8)); // description (add null-check if needed)

			    // Map bookType
			    String bookTypeValue = rs.getString(9); // bookType
			    if (bookTypeValue != null) {
			        b.setBookType(enums.BookType.valueOf(bookTypeValue));
			    } else {
			        b.setBookType(enums.BookType.Regular); // Provide a default value if null
			    }

			    // Fetch categories
			    stmt = conn.prepareStatement(getCategoriesQuery);
			    stmt.setString(1, catalogNumber);
			    rs = stmt.executeQuery();
			    ArrayList<String> categories = new ArrayList<>();
			    while (rs.next()) {
			        categories.add(rs.getString(1));
			    }
			    b.setCategories(categories);

			    // Log the populated book
			    System.out.println("Populated Book: " + b);

			    mp.addToCommPipe(b);
			}


		} catch (SQLException e) {
			e.printStackTrace();
		}
		return mp;
	}

	/**
	 * adds a new book copy tuple into bookcopies table.
	 * @param msg the parameters
	 * @return the return message
	 * @throws ParseException thrown should parsing the date encounter problems.
	 */
	public MsgParser addCopy(MsgParser mp) throws ParseException {
		PreparedStatement stmt;
		// get the argument from the MsgParser..
		BookCopies bc = (BookCopies) mp.getCommPipe().get(0);
		mp.clearCommPipe();
		// count total number of copies to numberOfcopies field
		try {
			// get total number copies of book
			String getTotalNumberOfCopies = "SELECT COUNT(*) " + "FROM bookcopies BC " + "WHERE BC.catalognumber = ?";
			stmt = conn.prepareStatement(getTotalNumberOfCopies);
			stmt.setString(1, bc.getCatalogNumber());
			ResultSet rs = stmt.executeQuery();
			int totalNumberOfCopies = 0;
			if (rs.next())
				totalNumberOfCopies = rs.getInt(1);
			// --------------------------------------------------------
			// get actual number of copies
			String getActualNumberOfCopies = "SELECT B.numberOfCopies " + "FROM books B " + "WHERE B.catalognumber = ?";
			stmt = conn.prepareStatement(getActualNumberOfCopies);
			stmt.setString(1, bc.getCatalogNumber());
			rs = stmt.executeQuery();
			int numberOfCopies = 0;
			if (rs.next())
				numberOfCopies = rs.getInt(1);
			// compare the the two numbers
			if (totalNumberOfCopies == numberOfCopies)// if they're equal don't add the copy.
				return mp;
		} catch (SQLException e1) {
			e1.printStackTrace();
			return mp;
		}
		String addCopyQuery = "INSERT INTO bookcopies VALUES(?,?,?,?)";
		String catalogNumber = bc.getCatalogNumber();
		String barcode = bc.getBarcode();
		// get the date and convert it to SQL Date.
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		Date input = bc.getPurchaseDate();
		String pd = df.format(input);
		Date temp = new SimpleDateFormat("yyyy-MM-dd").parse(pd);
		// java.sql.Date d = new java.sql.Date(temp.getDate());
		java.sql.Date purchaseDate = new java.sql.Date(temp.getTime());
		enums.BookCopyStatus status = bc.getStatus();
		try {
			stmt = conn.prepareStatement(addCopyQuery);
			stmt.setString(1, barcode);
			stmt.setString(2, catalogNumber);
			stmt.setDate(3, purchaseDate);
			stmt.setString(4, status.name());
			if (stmt.executeUpdate() > 0)
				mp.addToCommPipe(true);
			else
				mp.addToCommPipe(false);
		} catch (SQLException e) {
			e.printStackTrace();
			mp.addToCommPipe(false);
		}
		return mp;
	}

	/**
	 * gets all faults for a specific user
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser userFaultsHistory(MsgParser msg) {
		PreparedStatement stmt;
		FaultsHistory tmpFaultsHistory = null;
		String username = ((User) msg.getCommPipe().get(0)).getUserID();
		try {
			String getFaultsHistoryQuery = "SELECT * FROM faultshistory F WHERE F.userID = ? ";
			stmt = conn.prepareStatement(getFaultsHistoryQuery);
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			// get the matching tuples, if there's any
			msg.clearCommPipe();
			while (rs.next()) {
				tmpFaultsHistory = new FaultsHistory();
				tmpFaultsHistory.setUserID(rs.getString(1));
				tmpFaultsHistory.setFaultDesc(rs.getString(2));
				tmpFaultsHistory.setFaultDate(rs.getDate(3));
				msg.addToCommPipe(tmpFaultsHistory);
			}

		} catch (SQLException e) {
			msg.addToCommPipe(tmpFaultsHistory);
		}
		return msg;
	}


	/**
	 * sets the status of the book copy to 'borrowed'
	 * @param msg the parameters
	 * @return the return message
	 * @throws ParseException thrown should parsing the date encounter problems.
	 */
	public MsgParser updatebookcopytable(MsgParser msg) throws ParseException {
		PreparedStatement stmt;

		try {
			stmt = conn.prepareStatement("UPDATE bookcopies SET status = 'borrowed' WHERE barcode = ?");
			stmt.setString(1, ((BookCopies) msg.getCommPipe().get(0)).getBarcode());
			stmt.executeUpdate();
			msg.clearCommPipe();
			msg.setUpdatecopyresult(UpdateCopyResult.Success);

		} catch (SQLException e) {

			msg.setUpdatecopyresult(UpdateCopyResult.Fail);
		}
		return msg;

	}

	/**
	 * checks if the specified borrow is active.
	 * @param msg the parameters
	 * @return the return message
	 * @throws ParseException thrown should parsing the date encounter problems.
	 */
	public MsgParser checkborrow(MsgParser msg) throws ParseException {
		PreparedStatement stmt;
		try {
			String getActiveBorrrowsQuery = "SELECT * " + "FROM borrows B "
					+ "WHERE (B.userID = ? AND B.barcode = ? AND (B.status= 'Active' OR B.status = 'LateNotReturned'))";
			stmt = conn.prepareStatement(getActiveBorrrowsQuery);
			stmt.setString(1, ((Borrows) msg.getCommPipe().get(0)).getUserID());
			stmt.setString(2, ((Borrows) msg.getCommPipe().get(0)).getBarcode());
			// stmt.setString(3, ((Borrows) msg.getCommPipe().get(0)).getLibrarianID());
			ResultSet rs = stmt.executeQuery();
			msg.clearCommPipe();
			if (rs.next()) {

				msg.setBorrowDate(rs.getTimestamp(4));
				msg.setReturnDate(rs.getDate(5));
				msg.setIsExist(enums.ExistStatus.Exist);
			}

			else {
				msg.setIsExist(enums.ExistStatus.NotExist);
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return msg;

	}

	/**
	 * updates the return date of a borrow
	 * @param msg the parameters
	 * @return the return message
	 * @throws ParseException thrown should parsing the date encounter problems.
	 */
	public MsgParser UpdateBorrowTableAfterDelaying(MsgParser msg) throws ParseException {
		PreparedStatement stmt;
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date returnDate = ((Borrows) msg.getCommPipe().get(0)).getReturnDate();
		String retDate = df.format(returnDate);
		Date utilDate2 = new SimpleDateFormat("yyyy-MM-dd").parse(retDate);
		java.sql.Date sqlDate2 = new java.sql.Date(utilDate2.getTime());
		try {

			stmt = conn.prepareStatement(
					"UPDATE borrows SET returnDate = ? WHERE (userID = ? AND barcode = ? AND librarianID = ? AND status= 'Active')");
			stmt.setDate(1, sqlDate2);
			stmt.setString(2, ((Borrows) msg.getCommPipe().get(0)).getUserID());
			stmt.setString(3, ((Borrows) msg.getCommPipe().get(0)).getBarcode());
			stmt.setString(4, ((Borrows) msg.getCommPipe().get(0)).getLibrarianID());
			stmt.executeUpdate();
			msg.clearCommPipe();
			msg.setBorrowresult(Result.Success);

		} catch (SQLException e) {

			msg.setBorrowresult(Result.Fail);
		}
		return msg;

	}

	/**
	 * inserts a new manual delays tuple.
	 * @param msg the parameters
	 * @return the return message
	 * @throws ParseException thrown should parsing the date encounter problems.
	 */
public MsgParser UpdateDelayTableTask(MsgParser msg) throws ParseException {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    PreparedStatement stmt, stmt2, stmtFetchLibrarian;
    String librarianName = null;

    String getSpecificDelayQuery = "SELECT * " +
            "FROM manualdelays M " +
            "WHERE (M.LibrarianID = ? AND M.date = ? AND M.userID = ? AND M.barcode= ? AND M.borrowdate=?)";

    String fetchLibrarianNameQuery = "SELECT firstName, lastName FROM users WHERE userID = ?";

    Date date = ((ManualDelays) msg.getCommPipe().get(0)).getDate();
    Timestamp borrowDate = ((ManualDelays) msg.getCommPipe().get(0)).getBorrowDate();
    String retDate = df.format(date);
    String borrDate = df.format(borrowDate);
    Date utilDate1 = new SimpleDateFormat("yyyy-MM-dd").parse(borrDate);
    Date utilDate2 = new SimpleDateFormat("yyyy-MM-dd").parse(retDate);
    java.sql.Date sqlDate2 = new java.sql.Date(utilDate2.getTime());
    java.sql.Date sqlDate1 = new java.sql.Date(utilDate1.getTime());
    String s = borrowDate.toString().split("\\.")[0];

    try {
        // Fetch librarian name from the `users` table
        stmtFetchLibrarian = conn.prepareStatement(fetchLibrarianNameQuery);
        stmtFetchLibrarian.setString(1, ((ManualDelays) msg.getCommPipe().get(0)).getLibraraianID());
        ResultSet rsLibrarian = stmtFetchLibrarian.executeQuery();
        if (rsLibrarian.next()) {
            librarianName = rsLibrarian.getString("firstName") + " " + rsLibrarian.getString("lastName");
        } else {
            msg.setDelayResult(Result.Fail);
            msg.addToCommPipe("Librarian not found!");
            return msg; // Exit if librarian ID does not exist
        }
    } catch (SQLException e) {
        e.printStackTrace();
        msg.setDelayResult(Result.Fail);
        msg.addToCommPipe("Error fetching librarian name!");
        return msg;
    }

    try {
        stmt2 = conn.prepareStatement(getSpecificDelayQuery);
        stmt2.setString(1, ((ManualDelays) msg.getCommPipe().get(0)).getLibraraianID());
        stmt2.setDate(2, sqlDate2);
        stmt2.setString(3, ((ManualDelays) msg.getCommPipe().get(0)).getUserID());
        stmt2.setString(4, ((ManualDelays) msg.getCommPipe().get(0)).getBarcode());
        stmt2.setString(5, s);
        ResultSet rs = stmt2.executeQuery();
        if (rs.next()) {
            msg.setDelayResult(Result.Occured);
            return msg;
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }

    try {
        stmt = conn.prepareStatement("INSERT INTO manualdelays VALUES(?,?,?,?,?,?)");
        stmt.setString(1, ((ManualDelays) msg.getCommPipe().get(0)).getLibraraianID());
        stmt.setDate(2, sqlDate2);
        stmt.setString(3, ((ManualDelays) msg.getCommPipe().get(0)).getUserID());
        stmt.setString(4, ((ManualDelays) msg.getCommPipe().get(0)).getBarcode());
        stmt.setString(5, s);
        stmt.setString(6, librarianName); // Use the retrieved librarianName
        stmt.executeUpdate();
        msg.clearCommPipe();
        msg.setDelayResult(Result.Success);

    } catch (SQLException e) {
        e.printStackTrace();
        msg.setDelayResult(Result.Fail);
    }
    return msg;
}


	/**
	 * checks the status of a member
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser changeMemberStatus(MsgParser msg) {
		PreparedStatement stmt;
		String userID = ((User) msg.getCommPipe().get(0)).getUserID();
		String newStatus = ((User) msg.getCommPipe().get(0)).getStatus().toString();
		try {
			String changeMemberStatusQuery = "UPDATE users SET status= ? WHERE userID = ?";
			stmt = conn.prepareStatement(changeMemberStatusQuery);
			stmt.setString(2, userID);
			stmt.setString(1, newStatus);
			int rs = stmt.executeUpdate();
			// get the matching tuples, if there's any
			msg.clearCommPipe();
			if (rs > 0)
				msg.addToCommPipe(true);
			else
				msg.addToCommPipe(false);
		} catch (SQLException e) {
			msg.addToCommPipe(false);
		}
		return msg;
	}


	/**
	 * gets all existing categories in the DB.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser getAllCategories(MsgParser msg) {
		Statement stmt;
		ResultSet rs;
		try {
			stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT DISTINCT C.categoryName FROM categories C");
			msg.clearCommPipe();
			while (rs.next()) {
				msg.addToCommPipe(rs.getString(1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return msg;
	}



	/**
	 * gets number of available book copies.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser getNumberOfAvailableCopies(MsgParser msg) {
		PreparedStatement stmt;
		ResultSet rs;
		String catalogNumber = (String) msg.getCommPipe().get(0);
		msg.clearCommPipe();
		int availableCopies = 0;
		String availableCopiesQuery = "SELECT COUNT(*) " + "FROM bookcopies BC "
				+ "WHERE BC.catalognumber = ? AND BC.status = 'available'";
		try {
			stmt = conn.prepareStatement(availableCopiesQuery);
			stmt.setString(1, catalogNumber);
			rs = stmt.executeQuery();
			if (rs.next()) {
				availableCopies = rs.getInt(1);
				msg.addToCommPipe(availableCopies);
			}
		} catch (SQLException e) {
			e.printStackTrace();

		}
		return msg;
	}

	/**
	 * gets number of pending reservations for a book.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser getNumberOfReserves(MsgParser msg) {
		PreparedStatement stmt;
		ResultSet rs;
		String catalogNumber = (String) msg.getCommPipe().get(0);
		msg.clearCommPipe();
		int reserves = 0;
		String getReservesQuery = "SELECT COUNT(*) " + "FROM reservations R, bookcopies BC "
				+ "WHERE BC.catalognumber = ? AND BC.barcode = R.barcode AND (R.reservestatus = 'Pending' OR R.reservestatus = 'twoDaysPending')";
		try {
			stmt = conn.prepareStatement(getReservesQuery);
			stmt.setString(1, catalogNumber);
			rs = stmt.executeQuery();
			if (rs.next()) {
				reserves = rs.getInt(1);
				msg.addToCommPipe(reserves);
			}
		} catch (SQLException e) {
			e.printStackTrace();

		}
		return msg;
	}

	/**
	 * adds a reservation tuple to the reservations table.
	 * @param msg the parameters
	 * @return the return message
	 */
public MsgParser addReserve(MsgParser msg) {
    PreparedStatement stmt;
    ResultSet rs;
    String userID = (String) msg.getCommPipe().get(0);
    String catalogNumber = (String) msg.getCommPipe().get(1);
    String barcode = "";
    msg.clearCommPipe();

    String getBarcodeQuery = "SELECT BC.barcode "
            + "FROM bookcopies BC "
            + "JOIN borrows B ON BC.barcode = B.barcode "
            + "WHERE BC.catalognumber = ? "
            + "AND (B.status = 'Active' OR B.status = 'LateNotReturned') "
            + "AND BC.barcode NOT IN (SELECT R.barcode "
            + "FROM reservations R "
            + "JOIN bookcopies BC1 ON R.barcode = BC1.barcode "
            + "WHERE R.reservestatus = 'Pending' AND BC1.catalogNumber = BC.catalogNumber) "
            + "ORDER BY B.ReturnDate ASC";

    try {
        stmt = conn.prepareStatement(getBarcodeQuery);
        stmt.setString(1, catalogNumber);
        rs = stmt.executeQuery();
        // There will always be a barCode to reserve; the situation in which the user
        // cannot reserve has already been handled
        if (rs.next()) {
            barcode = rs.getString(1);
        }
        stmt.close();

        String checkIfCanReserve = "SELECT R.userID,R.barcode "
                + "FROM reservations R, bookcopies BC "
                + "WHERE (R.userID = ? AND (BC.catalogNumber = ? AND R.barcode = BC.barcode)) AND ((R.userID,R.barcode) IN (SELECT R1.userID,R1.barcode "
                + "FROM reservations R1 "
                + "WHERE (R1.reservestatus = 'Pending' OR R1.reservestatus = 'twoDaysPending')))";

        stmt = conn.prepareStatement(checkIfCanReserve);
        stmt.setString(1, userID);
        stmt.setString(2, catalogNumber);
        rs = stmt.executeQuery();

        if (rs.next()) {
            msg.addToCommPipe(1); // User already reserved the book
        } else {
            // NEW: Check the user's status to determine if they can reserve
            String getUserStatusQuery = "SELECT status FROM users WHERE userID = ?";
            stmt = conn.prepareStatement(getUserStatusQuery);
            stmt.setString(1, userID);
            rs = stmt.executeQuery();

            if (rs.next()) {
                String status = rs.getString("status");
                if (status.equals("Active")) { // User can reserve
                    String addReserveQuery = "INSERT INTO reservations VALUES(?,?,NOW(),'Pending')";
                    stmt = conn.prepareStatement(addReserveQuery);
                    stmt.setString(1, userID);
                    stmt.setString(2, barcode);
                    if (stmt.executeUpdate() > 0) {
                    	
                    	// Successfully added reservation
                    	msg.addToCommPipe(0); 

                    	// Check if the book's type needs to be updated to "Wanted"
                    	String checkPendingReservationsQuery = "SELECT COUNT(*) FROM reservations R "
                    	        + "JOIN bookcopies BC ON R.barcode = BC.barcode "
                    	        + "WHERE BC.catalogNumber = ? AND R.reserveStatus = 'Pending'";

                    	try (PreparedStatement stmtCheckPending = conn.prepareStatement(checkPendingReservationsQuery)) {
                    	    stmtCheckPending.setString(1, catalogNumber);
                    	    try (ResultSet rsPending = stmtCheckPending.executeQuery()) {
                    	        if (rsPending.next() && rsPending.getInt(1) > 0) {
                    	            // If there are pending reservations, update the book type to "Wanted"
                    	            String updateBookTypeQuery = "UPDATE books SET type = 'Wanted' WHERE catalogNumber = ?";
                    	            try (PreparedStatement stmtUpdateType = conn.prepareStatement(updateBookTypeQuery)) {
                    	                stmtUpdateType.setString(1, catalogNumber);
                    	                int rowsUpdated = stmtUpdateType.executeUpdate();

                    	                if (rowsUpdated > 0) {
                    	                    System.out.println("Book type updated to Wanted for catalogNumber: " + catalogNumber);
                    	                } else {
                    	                    System.out.println("Failed to update book type to Wanted for catalogNumber: " + catalogNumber);
                    	                }
                    	            }
                    	        }
                    	    }
                    	} catch (SQLException e) {
                    	    System.err.println("Error updating book type to Wanted: " + e.getMessage());
                    	}

                    	
                    	
                        msg.addToCommPipe(0); // Successfully added reservation
                    } else {
                        msg.addToCommPipe(2); // Couldn't insert the tuple into the table
                    }
                } else { // User cannot reserve
                    msg.addToCommPipe(3); // User's status does not allow reserving
                }
            } else {
                msg.addToCommPipe(4); // User not found
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
        msg.addToCommPipe(-1);
    }

    return msg;
}

	/**
	 * checks if a reservation exists.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser checkReserveExistence(MsgParser msg) {
		// PreparedStatement stmt;
		String userID = ((Reservations) msg.getCommPipe().get(0)).getUserID();
		String barcode = ((Reservations) msg.getCommPipe().get(0)).getBarcode();
		msg.clearCommPipe();
		for (TwoDaysMessage td : timerList) {
			if (barcode.equals(td.getRealBarcode())) {
				if (userID.equals(td.getReservation().getUserID())) {
					msg.addToCommPipe(1);
					return msg;
				} else {
					msg.addToCommPipe(-1);
					return msg;
				}
			}
		}
		msg.addToCommPipe(0);
//		try {
//			
//				String checkReserve = "SELECT * " + "FROM reservations R "
//						+" WHERE R.barcode = ? AND R.reserveStatus = 'Pending'"+ "ORDER BY reserveDate";
//				stmt = conn.prepareStatement(checkReserve);
//				stmt.setString(1, barcode);
//				ResultSet rs = stmt.executeQuery();
//			if(rs.next()) {
//				if(userID.equals(rs.getString(1))) {
//					msg.addToCommPipe(true);
//					return msg;
//				}
//				else {
//					msg.addToCommPipe(false);
//					return msg;
//				}
//			}
//			
//		} catch (SQLException e) {
//			msg.addToCommPipe(false);
//		}
//		msg.addToCommPipe(false);
		return msg;
	}

	/**
	 * updates the reservation status to 'Closed'.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser updateReservestatusToDone(MsgParser msg) {
		
		PreparedStatement stmt;
		String userID = ((Reservations) msg.getCommPipe().get(0)).getUserID();
		String realbarcode = ((Reservations) msg.getCommPipe().get(0)).getBarcode();
		String barcode;
		int ind;
		for (TwoDaysMessage td : timerList) {
			if (realbarcode.equals(td.getRealBarcode())) {
				if (userID.equals(td.getReservation().getUserID())) {
					
					barcode = td.getReservation().getBarcode();
					try {
						String changeReserveStatusQuery = "UPDATE reservations SET reserveStatus= 'Closed' WHERE userID = ? AND barcode= ? AND (reserveStatus= 'Pending' OR reserveStatus= 'twoDaysPending')";
						stmt = conn.prepareStatement(changeReserveStatusQuery);
						stmt.setString(1, userID);
						stmt.setString(2, barcode);
						stmt.executeUpdate();
						msg.clearCommPipe();
						td.getTimer().cancel();
						timerList.remove(td);
						msg.setUpdateReservationsResult(Result.Success);

					} catch (SQLException e) {
						msg.setUpdateReservationsResult(Result.Fail);
					}
					return msg;
				}
			}
		}
		return msg;
	}

	/**
	 * gets all student users from the DB.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser getAllMembers(MsgParser msg) {
		Statement stmt;
		ResultSet rs;
		User tmpUser = null;
		try {
			stmt = conn.createStatement();
			rs = stmt.executeQuery(
					"SELECT U.* FROM users U WHERE NOT EXISTS ( SELECT L.librarianID FROM librarians L WHERE L.librarianID = U.userID )");
			while (rs.next()) {
				tmpUser = new User();
				tmpUser.setUserID(rs.getString(1));
				tmpUser.setFirstName(rs.getString(2));
				tmpUser.setLastName(rs.getString(3));
				tmpUser.setPhoneNumber(rs.getString(4));
				tmpUser.setMembershipNumber(rs.getString(5));
				tmpUser.setPassword(rs.getString(6));
				tmpUser.setStatus(enums.UserStatus.valueOf(rs.getString(7)));
				tmpUser.setEmail(rs.getString(8));
				msg.addToCommPipe(tmpUser);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return msg;
	}

	/**
	 * sends a message to the librarian notifying about a delay.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser sendMessageForDelay(MsgParser msg) {
		PreparedStatement stmt;
		Message message = (Message) msg.getCommPipe().get(0);
		try {
			String sendMessage = "INSERT INTO messages VALUES (?,?,?,?,NOW(),?)";
			stmt = conn.prepareStatement(sendMessage);
			stmt.setString(1, message.getMessageType().name());
			stmt.setString(2, message.getTitle());
			stmt.setString(3, message.getMsg());
			stmt.setString(4, message.getBelong());
			stmt.setString(5, message.getUser());
			stmt.executeUpdate();
			msg.clearCommPipe();
			msg.setDelayResult(Result.Success);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			msg.setDelayResult(Result.Fail);
		}
		return msg;
	}

	/**
	 * gets all employees
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser getAllEmployees(MsgParser msg) {
		Statement stmt;
		ResultSet rs;
		Librarian tmpLibrarian = null;
		try {
			stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT * FROM users U, librarians L WHERE U.userID = L.librarianID");
			while (rs.next()) {
				tmpLibrarian = new Librarian();
				tmpLibrarian.setUserID(rs.getString(1));
				tmpLibrarian.setFirstName(rs.getString(2));
				tmpLibrarian.setLastName(rs.getString(3));
				tmpLibrarian.setPhoneNumber(rs.getString(4));
				tmpLibrarian.setEmployeeNumber(rs.getString(12));
				tmpLibrarian.setPassword(rs.getString(6));
				tmpLibrarian.setDepartmentName(rs.getString(14));
				tmpLibrarian.setRole(rs.getString(13));
				tmpLibrarian.setEmail(rs.getString(9));
				msg.addToCommPipe(tmpLibrarian);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return msg;
	}




	/**
	 * check if the user already reserved the book.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser checkIfReserved(MsgParser msg) {
		PreparedStatement stmt;
		ResultSet rs;
		String catalognum = ((BookCopies) msg.getCommPipe().get(0)).getCatalogNumber();
		msg.clearCommPipe();
		try {
			String checkIfReserve = "SELECT R.barcode " + "FROM reservations R, bookcopies BC "
					+ "WHERE ((BC.catalogNumber = ? AND R.barcode = BC.barcode)) AND ((R.barcode) IN (SELECT R1.barcode "
					+ "FROM reservations R1 " + "WHERE R1.reservestatus = 'Pending'))";
			stmt = conn.prepareStatement(checkIfReserve);
			stmt.setString(1, catalognum);
			rs = stmt.executeQuery();
			if (rs.next()) {
				msg.addToCommPipe(-1);
				return msg;
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			msg.addToCommPipe(0);
			return msg;
		}
		msg.addToCommPipe(1);
		return msg;
	}

	/**
	 * deletes a message from the messages table.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser deleteMessageTuple(MsgParser msg) {
		PreparedStatement stmt;
		Message toDel = ((Message) msg.getCommPipe().get(0));
		msg.clearCommPipe();
		try {
			String deleteQuery = "DELETE FROM messages WHERE messsageType = ? AND belong = ? AND user= ?";
			stmt = conn.prepareStatement(deleteQuery);
			stmt.setString(1, toDel.getMessageType().name());
			stmt.setString(2, toDel.getBelong());
			stmt.setString(3, toDel.getUser());
			int rs = stmt.executeUpdate();
			if (rs == 0)
				msg.addToCommPipe(false);
			else
				msg.addToCommPipe(true);
		} catch (SQLException e) {
			e.printStackTrace();
			msg.addToCommPipe(false);
		}
		return msg;
	}

	/**
	 * adds a fault to a user.
	 * @param msg the parameters
	 * @return the return message
	 */
	public MsgParser addFault(MsgParser msg) {
		PreparedStatement stmt;
		FaultsHistory faultHistory = (FaultsHistory) msg.getCommPipe().get(0);
		try {
			String query = "INSERT INTO faultshistory VALUES (?,?,NOW())";
			stmt = conn.prepareStatement(query);
			stmt.setString(1, faultHistory.getUserID());
			stmt.setString(2, faultHistory.getFault());
			stmt.executeUpdate();
			msg.clearCommPipe();
			msg.addToCommPipe(true);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			msg.addToCommPipe(false);
		}
		return msg;
	}



	/**
	 * check if the specific user has an active borrow
	 * @param ID the user ID
	 * @return true if exists, false otherwise.
	 */
	public boolean checkBorrowsExistance(String ID) {
		PreparedStatement stmt;
		String getActiveBorrowsQuery = "SELECT * " + "FROM borrows B "
				+ "WHERE B.userID = ? AND (B.status = 'Active' OR B.status = 'LateNotReturned')";
		try {
			stmt = conn.prepareStatement(getActiveBorrowsQuery);
			stmt.setString(1, ID);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				return true;
			} else {
				return false;
			}

		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}

	}


	public MsgParser getEarliestReturnDate(MsgParser msg) {
		String catalogNumber = (String) msg.getCommPipe().get(0);
		String earliestReturnDate = null;
		 String query = "SELECT " +
                 "    CASE " +
                 "        WHEN COUNT(bc.barcode) = COUNT(CASE WHEN b.status = 'Active' THEN 1 END) " +
                 "        THEN MIN(b.returnDate) " +
                 "        ELSE 'Available' " +
                 "    END AS earliestReturnDate " +
                 "FROM " +
                 "    bookcopies bc " +
                 "LEFT JOIN " +
                 "    borrows b ON bc.barcode = b.barcode AND bc.status IN ('borrowed') " + 
                 "WHERE " +
                 "    bc.catalogNumber = ?";


		try (PreparedStatement ps = conn.prepareStatement(query)) {
			ps.setString(1, catalogNumber); // Set the catalogNumber parameter
			ResultSet rs = ps.executeQuery();

			if (rs.next()) {
				earliestReturnDate = rs.getString("earliestReturnDate");
				if (earliestReturnDate == null) {
					System.out.println("No return date found for the given catalog number.");
					msg.clearCommPipe();
					msg.addToCommPipe(null);
				} else {
					System.out.println("Earliest return date: " + earliestReturnDate);
					msg.clearCommPipe();
					msg.addToCommPipe(earliestReturnDate);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return msg;
	}



public static int[] getUserCountsByStatus() {
    int[] userCounts = new int[2]; // [active, frozen]
    String query = "SELECT status, COUNT(*) AS count FROM users GROUP BY status";

    try (PreparedStatement stmt = conn.prepareStatement(query);
         ResultSet rs = stmt.executeQuery()) {

        while (rs.next()) {
            String status = rs.getString("status");
            int count = rs.getInt("count");

            switch (status) {
                case "Active":
                    userCounts[0] = count;
                    break;
                case "Frozen":
                    userCounts[1] = count;
                    break;
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }

    return userCounts;
}

public static List<String> getLibrarianEmails() {
    List<String> emailList = new ArrayList<>();
    String query = "SELECT u.email " +
                   "FROM librarians l " +
                   "JOIN users u ON l.librarianID = u.userID " +
                   "WHERE u.email IS NOT NULL";

    try (PreparedStatement stmt = conn.prepareStatement(query);
         ResultSet rs = stmt.executeQuery()) {

        while (rs.next()) {
            emailList.add(rs.getString("email"));
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }

    return emailList;
}


// Method to fetch borrowing period data
public Map<String, Map<String, Integer>> fetchBorrowingPeriodData(int month, int year) {
    // Define result maps
    Map<String, Integer> onTimeReturns = new HashMap<>();
    Map<String, Integer> lateReturns = new HashMap<>();

    // Initialize ranges
    String[] durationRanges = {"0-7 days", "8-14 days", "15-21 days", "22+ days"};
    String[] lateRanges = {"1-7 days late", "8-14 days late", "15+ days late"};

    for (String range : durationRanges) {
        onTimeReturns.put(range, 0);
    }

    for (String range : lateRanges) {
        lateReturns.put(range, 0);
    }

    // SQL query to fetch borrowing data
    String query = "SELECT borrowDate, returnDate, actualReturnDate, status FROM borrows " +
                   "WHERE MONTH(borrowDate) = ? AND YEAR(borrowDate) = ?";

    try (PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setInt(1, month);
        stmt.setInt(2, year);

        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
            LocalDate borrowDate = rs.getDate("borrowDate").toLocalDate();
            LocalDate returnDate = rs.getDate("returnDate").toLocalDate();
            java.sql.Date actualReturnDateSQL = rs.getDate("actualReturnDate");

            // Skip rows where actualReturnDate is NULL
            if (actualReturnDateSQL == null) {
                continue;
            }

            // Convert java.sql.Date to java.time.LocalDate
            LocalDate actualReturnDate = actualReturnDateSQL.toLocalDate();

            long borrowingDuration = ChronoUnit.DAYS.between(borrowDate, actualReturnDate);

            if (!actualReturnDate.isAfter(returnDate)) { // On-time return
                if (borrowingDuration <= 7) {
                    onTimeReturns.put("0-7 days", onTimeReturns.get("0-7 days") + 1);
                } else if (borrowingDuration <= 14) {
                    onTimeReturns.put("8-14 days", onTimeReturns.get("8-14 days") + 1);
                } else if (borrowingDuration <= 21) {
                    onTimeReturns.put("15-21 days", onTimeReturns.get("15-21 days") + 1);
                } else {
                    onTimeReturns.put("22+ days", onTimeReturns.get("22+ days") + 1);
                }
            } else { // Late return
                long lateness = ChronoUnit.DAYS.between(returnDate, actualReturnDate);

                if (lateness <= 7) {
                    lateReturns.put("1-7 days late", lateReturns.get("1-7 days late") + 1);
                } else if (lateness <= 14) {
                    lateReturns.put("8-14 days late", lateReturns.get("8-14 days late") + 1);
                } else {
                    lateReturns.put("15+ days late", lateReturns.get("15+ days late") + 1);
                }
            }
        }

        // Debug output for on-time returns
        System.out.println("On-Time Returns:");
        for (Map.Entry<String, Integer> entry : onTimeReturns.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        // Debug output for late returns
        System.out.println("Late Returns:");
        for (Map.Entry<String, Integer> entry : lateReturns.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

    } catch (SQLException e) {
        e.printStackTrace();
    }

    // Combine results into a single map
    Map<String, Map<String, Integer>> result = new HashMap<>();
    result.put("onTimeReturns", onTimeReturns);
    result.put("lateReturns", lateReturns);

    return result;
}


public void generateMonthlyReports(LocalDate testDate) {
    LocalDate today = testDate != null ? testDate : LocalDate.now(); // Use testDate if provided, otherwise use the current date.

    // Check if today is the first of the month
    if (today.getDayOfMonth() != 1) {
        System.out.println("Not the first of the month. Reports will not be generated.");
        return;
    }

    try {
        // Generate the Activity Status Report
        ActivityStatusReportGenerator activityReportGenerator = new ActivityStatusReportGenerator();
        File activityStatusReport = activityReportGenerator.createChart();

        // Fetch borrowing data for the previous month
        int previousMonth = today.minusMonths(1).getMonthValue();
        int year = today.minusMonths(1).getYear();
        Map<String, Map<String, Integer>> borrowingData = fetchBorrowingPeriodData(previousMonth, year);

        File borrowingPeriodReport = null;
        if (borrowingData != null && !borrowingData.isEmpty()) {
            // Generate the Borrowing Period Report
            BorrowingPeriodChartGenerator borrowingChartGenerator = new BorrowingPeriodChartGenerator();
            borrowingPeriodReport = new File("BorrowingPeriodReport.png");
            borrowingChartGenerator.generateBorrowingPeriodChart(borrowingData, borrowingPeriodReport.getAbsolutePath());
        } else {
            System.out.println("No borrowing data found for the previous month. Skipping Borrowing Period Report.");
        }

        // Combine both reports into a single array
        File[] reports = {activityStatusReport, borrowingPeriodReport};

        // Fetch librarian emails
        List<String> librarianEmails = getLibrarianEmails();

        // Send reports to all librarians
        String subject = "Monthly Reports of " + today.minusMonths(1).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        for (String email : librarianEmails) {
            ReportEmailController.sendReportEmail(email, subject, "", reports);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}


}
