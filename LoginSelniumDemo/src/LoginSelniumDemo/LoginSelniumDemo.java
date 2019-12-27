package LoginSelniumDemo;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.File;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
import org.apache.poi.sl.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class LoginSelniumDemo {

	public static void main(String[] args) throws FileNotFoundException {
		System.getProperty("webdriver,chrome,driver","C:\\navroz\\jars\\chromedriver_win32\\chromedriver.exe");
		WebDriver driver = new ChromeDriver();
		    FileInputStream fi = new FileInputStream("C:\\Users\\yirsh\\Desktop\\UAT WEBSHOP.xls");
		    XSSFWorkbook w = new  XSSFWorkbook(fi);
		    XSSFSheet sh1= w.getSheetAt(0);
		   // int z=sh1.getRows();
		   // System.out.println("no of Users:"+z);
		    driver.get("http://RexelQAEnv:Rexel!23@52.19.191.249/aus/");
		    driver.manage().window().maximize();
		    //Thread.sleep(10000);

		    for(int row=1; row <= 10; row++)
		    {

		        String username = sh1.getCell(2,row).getContents();
		        String user = sh1.getCell
		        System.out.println("Username: "+username);
		        driver.findElement(By.id("j_username_leftAside")).sendKeys(username);

		        String password= sh1.getCell(3,row).getContents();
		        System.out.println("Password: "+password);
		        driver.findElement(By.id("j_password_leftAside")).sendKeys(password);

		        driver.findElement(By.xpath("/html[@class=' js opacity generatedcontent pointerevents']/body[@class='page-homepage pageType-ContentPage template-pages-layout-RexelHomePageLayout pageLabel-homepage language-de ']/div[@id='page']/div[@id='content']/div[@id='content']/div[@class='content-top-inner']/div[@id='content-inner']/div[@class='mid-wrapper'][1]/div[@class='yCmsContentSlot']/div[@class='login clear']/form[@id='loginForm']/div[@class='left sign-in']/button[@class='Sign-in rx-btn mb0']")).click();

		        if(driver.getPageSource().contains("Willkommen"))
		        {
		            System.out.println("Login Sucessfull");
		            // write code for logout
		        }
		        else
		        {
		        System.out.println("Login Failed");
		        }
	}
	}
}
