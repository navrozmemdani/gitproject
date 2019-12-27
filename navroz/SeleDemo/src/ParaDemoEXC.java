//Read and write in excel manually
import org.openqa.selenium.chrome.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
public class ParaDemoEXC {

	public static void main(String[] args) throws InterruptedException, IOException
	{
		
		File scr = new File ("D:\\Users\\Default User\\Desktop\\demo.xlsx");
		FileInputStream fis = new FileInputStream(scr);
		@SuppressWarnings("resource")
		XSSFWorkbook wb = new XSSFWorkbook(fis);
		XSSFSheet sh = wb.getSheetAt(0);
		System.setProperty("webdriver.chrome.driver","D:\\navroz\\eclipse-jee-oxygen-3a-win32-x86_64\\soft\\chromedriver_win32\\chromedriver.exe");
		WebDriver driver = new ChromeDriver();
		driver.manage().window().maximize();
		driver.get(" http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");
		driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
		try
		{
			String username = sh.getRow(0).getCell(0).getStringCellValue();
			String password = sh.getRow(0).getCell(1).getStringCellValue();
			driver.findElement(By.id("username")).sendKeys(username);
			driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
			driver.findElement(By.id("password")).sendKeys(password);
			Thread.sleep(3000);
			driver.findElement(By.id("submit-button")).click();
			driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
			driver.navigate().back();
			driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
			System.out.println("Successfully login using operations " + username +" "+ password );
			Row row = sh.createRow(0);
			Cell cell = row.createCell(3);
			cell.setCellValue("operation user");
			FileOutputStream fos = new FileOutputStream("D:\\Users\\Default User\\Desktop\\demo.xlsx");
			wb.write(fos);
			fos.close();
		}
		catch(NoSuchElementException e)
		{
			e.printStackTrace();
		}
		driver.close();
		
		
	}
}
