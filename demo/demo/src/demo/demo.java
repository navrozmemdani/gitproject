package demo;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.poi.sl.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Workbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public class demo {

    public static void main(String[] args) throws InterruptedException, IOException {
    	System.setProperty("webdriver.chrome.driver", "D:\\navroz\\eclipse-jee-oxygen-3a-win32-x86_64\\soft\\chromedriver_win32\\chromedriver.exe");
        WebDriver webDriver = new ChromeDriver();
        webDriver.get(" http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");
        
        FileInputStream fi = new FileInputStream("E:\\myExcelWorkBook.xls");
        Workbook W = Workbook.getWorkbook(fi);

        Sheet s = W.getSheet(0);

        for(int row = 0;row <= s.getRows();row++){

        String Username = s.getCell(0,row).getContents();
        System.out.println("Username" +Username);
        webDriver.get("AppURL");
        webDriver.findElement(By.id("txtUserName")).sendKeys(Username);

        String password= s.getCell(1, row).getContents();
        System.out.println("Password "+password);

        webDriver.findElement(By.id("txtPassword")).sendKeys(password);

        webDriver.findElement(By.id("btnLogin")).click();
       }
       webDriver.close();
        webDriver.quit();
    }
    }
}
