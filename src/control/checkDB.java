package control;

import java.text.ParseException;
import java.util.TimerTask;

public class checkDB extends TimerTask{
	DBController dbController;
	public checkDB(DBController dbController)
	{
		this.dbController=dbController;
	}
	@Override
	public void run() {
		checkLateReturn();
		checkDayBeforeReturn();
	}
	
	
	//function that check for late returns if any one has freeze his card
	public void checkLateReturn()
	{
		dbController.checkLateReturn();
		dbController.checkPenalty();
		
	}
	
	public void checkDayBeforeReturn()
	{
			dbController.checkDayBeforeReturnAndSendMessage();	
	}
	
	
}
