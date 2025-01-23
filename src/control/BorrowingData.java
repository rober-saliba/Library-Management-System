package control;
import java.util.Date;


public class BorrowingData {
    private String barcode;
    private Date borrowDate;
    private Date returnDate;
    private Date actualReturnDate;

    // Constructor
    public BorrowingData(String barcode, Date borrowDate, Date returnDate, Date actualReturnDate) {
        this.barcode = barcode;
        this.borrowDate = borrowDate;
        this.returnDate = returnDate;
        this.actualReturnDate = actualReturnDate;
    }

    // Getters
    public String getBarcode() {
        return barcode;
    }

    public Date getBorrowDate() {
        return borrowDate;
    }

    public Date getReturnDate() {
        return returnDate;
    }

    public Date getActualReturnDate() {
        return actualReturnDate;
    }
}
