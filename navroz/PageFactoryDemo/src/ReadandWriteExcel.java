
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
public class ReadandWriteExcel {
 public static void main(String []args){
 
  try {
	 System.setProperty("webdriver.chrome.driver","D:\\navroz\\eclipse-jee-oxygen-3a-win32-x86_64\\soft\\chromedriver_win32\\chromedriver.exe");
	WebDriver driver = new ChromeDriver();
	driver.get(" http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");
	driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
	File src=new File("D:\\Users\\Default User\\Desktop\\demo.xlsx");
  	FileInputStream fis=new FileInputStream(src);
   	@SuppressWarnings("resource")
   	XSSFWorkbook wb=new XSSFWorkbook(fis);
    XSSFSheet sh1= wb.getSheetAt(0);
    String username = sh1.getRow(0).getCell(0).getStringCellValue();
    String password = sh1.getRow(0).getCell(1).getStringCellValue();
    driver.findElement(By.id("username")).sendKeys(username);
	driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
	driver.findElement(By.id("password")).sendKeys(password);
	Thread.sleep(3000);
	
	String User = driver.findElement(By.id("username")).getText();
	//String pass = driver.findElement(By.id("username")).getText();
	//driver.findElement(By.id("submit-button")).click();
	driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
	
	 XSSFRow row1 = sh1.createRow(1);
	 XSSFCell cell1 = row1.createCell(1);
	 cell1.setCellValue(User);
	 FileOutputStream fout=new FileOutputStream(new File("D:\\Users\\Default User\\Desktop\\demo.xlsx"));
	 wb.write(fout);
	 fout.close();
	 driver.close();
  } catch (Exception e) {
	  System.out.println(e.getMessage());
  }
 
 }
 
}
 
