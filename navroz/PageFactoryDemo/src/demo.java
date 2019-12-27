//Read and Write in excel 
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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
public class demo {
 public static void main(String []args){
 
  try {
	 System.setProperty("webdriver.chrome.driver","D:\\navroz\\eclipse-jee-oxygen-3a-win32-x86_64\\soft\\chromedriver_win32\\chromedriver.exe");
	WebDriver driver = new ChromeDriver();
	driver.get("https://translate.google.co.in/#view=home&op=translate&sl=en&tl=es&text");
	driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
	File src=new File("D:\\Users\\Default User\\Desktop\\demo.xlsx");
  	// file is opened
	FileInputStream fis=new FileInputStream(src);
   	@SuppressWarnings("resource")
   	XSSFWorkbook wb=new XSSFWorkbook(fis);
    XSSFSheet sh1= wb.getSheetAt(0);
    String text = sh1.getRow(0).getCell(0).getStringCellValue();
    //data is send to the textbox
    driver.findElement(By.xpath("//textarea[@id='source']")).sendKeys(text);
	driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
	
	// output data obj 
	WebElement User = driver.findElement(By.xpath("//div[@class='result-shield-container tlid-copy-target']"));
	String user1 = User.getText();
	System.out.println(user1);
	//String pass = driver.findElement(By.id("username")).getText();
	//driver.findElement(By.id("submit-button")).click();
	//driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
	
	 XSSFRow row1 = sh1.createRow(0);
	 XSSFCell cell1 = row1.createCell(1);
	 cell1.setCellValue(user1);
	 FileOutputStream fout=new FileOutputStream(new File("D:\\Users\\Default User\\Desktop\\demo.xlsx"));
	 wb.write(fout);
	 fout.close();
	 driver.close();
  } catch (Exception e) {
	  System.out.println(e.getMessage());
  }
 
 }
 
}
 
