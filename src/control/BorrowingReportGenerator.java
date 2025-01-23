package control;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.data.gantt.Task;
import org.jfree.data.gantt.TaskSeries;
import org.jfree.data.gantt.TaskSeriesCollection;
import org.jfree.data.time.SimpleTimePeriod;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public class BorrowingReportGenerator {



    // Method to create the Gantt chart
private File createGanttChart(List<BorrowingData> borrowingData) {
    try {
        TaskSeries series = new TaskSeries("Borrowing Periods");

        for (BorrowingData data : borrowingData) {
            // Borrow, return, and actual return dates
            Date borrowDate = data.getBorrowDate();
            Date returnDate = data.getReturnDate();
            Date actualReturnDate = data.getActualReturnDate();

            // Ensure valid task range
            Date adjustedActualReturnDate = actualReturnDate != null ? actualReturnDate : returnDate;
            if (borrowDate.equals(adjustedActualReturnDate)) {
                adjustedActualReturnDate = new Date(borrowDate.getTime() + (24 * 60 * 60 * 1000)); // Add 1 day
            }

            // Main task: Borrowing period
            Task task = new Task("Book: " + data.getBarcode(),
                                 new SimpleTimePeriod(borrowDate.getTime(), adjustedActualReturnDate.getTime()));

            // On-Time Period (Green)
            if (actualReturnDate != null && actualReturnDate.before(returnDate)) {
                task.addSubtask(new Task("On-Time",
                                         new SimpleTimePeriod(borrowDate.getTime(), actualReturnDate.getTime())));
            }

            // Late Period (Red)
            if (actualReturnDate != null && actualReturnDate.after(returnDate)) {
                task.addSubtask(new Task("Late Return",
                                         new SimpleTimePeriod(returnDate.getTime(), actualReturnDate.getTime())));
            }

            // Add task to series
            series.add(task);
        }

        TaskSeriesCollection dataset = new TaskSeriesCollection();
        dataset.add(series);

        // Create the Gantt chart
        JFreeChart ganttChart = ChartFactory.createGanttChart(
            "Borrowing Periods of Books",
            "Books (Barcodes)", // Y-axis label
            "Days of the Month", // X-axis label
            dataset,
            true,
            true,
            false
        );

        // Customize the X-axis using a DateAxis
        CategoryPlot plot = (CategoryPlot) ganttChart.getPlot();
        DateAxis axis = new DateAxis("Days of the Month");
        SimpleDateFormat dayFormat = new SimpleDateFormat("d");
        axis.setDateFormatOverride(dayFormat);
        plot.setRangeAxis(axis);

        // Save the chart
        File chartFile = new File("BorrowingGanttChart.png");
        ChartUtils.saveChartAsPNG(chartFile, ganttChart, 1000, 600);

        return chartFile;

    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}


public File generateBorrowingReportForMonth(int month, int year) {
    try {
        // Step 1: Fetch borrowing data for the specified month and year
        List<BorrowingData> borrowingData = DBController.getBorrowingPeriods(month, year);

        if (borrowingData.isEmpty()) {
            System.out.println("No borrowing data found for the specified month and year.");
            return null;
        }

        // Step 2: Generate the Gantt chart
        return createGanttChart(borrowingData);

    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}

}
