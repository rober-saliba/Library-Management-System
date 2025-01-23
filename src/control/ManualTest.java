package control;

import java.io.File;
import java.util.List;

import entity.ConstantsAndGlobalVars;
import entity.MsgParser;
import enums.UserStatus;


public class ManualTest {
    public static void main(String[] args) {
        System.setProperty("mail.debug", "true");

        // Database connection details
        String username = "root";
        String password = "Aa123456";
        String host = "localhost"; // Change to your database host if needed
        String dbName = "blibdb";

        // Initialize the DBController instance
        DBController dbController = new DBController();

        // Connect to the database
        boolean isConnected = dbController.connectToDB(username, password, host, dbName);
        ActivityStatusReportGenerator generator = new ActivityStatusReportGenerator();

        if (isConnected) {
          // dbController.checkLateReturn();
            // Test the getUserStatusTask
            try {
                
            	
                // Simulated user data for testing
                //int[] testUserCounts = {754, 675}; // Active = 150, Frozen = 50

                // Generate the chart
                //File chartFile = generator.createChart(testUserCounts);

                //if (chartFile != null && chartFile.exists()) {
                  //  System.out.println("Chart generated successfully: " + chartFile.getAbsolutePath());
                //} else {
                  //  System.out.println("Failed to generate chart.");
                //}
            	
            	
            	//generator.generateReport();
            	
            	
                
                // Generate the Gantt chart
               /* BorrowingReportGenerator generator2 = new BorrowingReportGenerator();
                File chartFile = generator2.createGanttChart(borrowingDataList);

                if (chartFile != null && chartFile.exists()) {
                    System.out.println("Gantt chart generated successfully: " + chartFile.getAbsolutePath());
                } else {
                    System.out.println("Failed to generate Gantt chart.");
                }*/
            	
            	
            	
                BorrowingReportGenerator reportGenerator = new BorrowingReportGenerator();

                // Specify the month and year for the report
                int month = 1;  // January
                int year = 2025;

                // Generate the report
                File chartFile = reportGenerator.generateBorrowingReportForMonth(month, year);

                if (chartFile != null && chartFile.exists()) {
                    System.out.println("Gantt chart generated successfully: " + chartFile.getAbsolutePath());
                } else {
                    System.out.println("Failed to generate Gantt chart.");
                }
                
            	
            	
            	//testGetUserStatus(dbController);
            	
            	
                /*ActivityStatusReportGenerator generator = new ActivityStatusReportGenerator();
                int[] counts = generator.fetchData();
                System.out.println("Active Users: " + counts[0]);
                System.out.println("Frozen Users: " + counts[1]);*/
            	
            	
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("Testing completed.");
        } else {
            System.out.println("Failed to connect to the database. Exiting...");
        }
    }

    private static void testGetUserStatus(DBController dbController) {
        // Simulate a MsgParser to send to the DBController
        MsgParser<String> msg = new MsgParser<>();
        String testUserID = "319008264"; // Replace with a valid userID in your database
        msg.addToCommPipe(testUserID);

        // Call the method and print the result
        MsgParser response = dbController.getUserStatus(msg);
        if (!response.getCommPipe().isEmpty()) {
            String userStatus = (String) response.getCommPipe().get(0);
            System.out.println("User ID: " + testUserID + ", Status: " + userStatus);

            // Validate the status
            if (userStatus.equals(UserStatus.Active.name())) {
                System.out.println("The user is Active and can borrow/reserve.");
            } else if (userStatus.equals(UserStatus.Frozen.name())) {
                System.out.println("The user is Frozen and cannot borrow/reserve.");
            } else {
                System.out.println("Unexpected status: " + userStatus);
            }
        } else {
            System.out.println("No status returned for user ID: " + testUserID);
        }
    }
}
