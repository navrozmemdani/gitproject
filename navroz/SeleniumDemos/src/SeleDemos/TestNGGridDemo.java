//java -jar selenium-server-standalone-2.25.0.jar -port 4444 -role hub -nodeTimeout 1000

//java -Dwebdriver.ie.driver = D:\IEDriverServer.exe
//   -jar D:\JAR\selenium-server-standalone-2.42.2.jar
//   -role webdriver -hub http://10.30.217.157:4444/grid/register
//  -browser browserName = ie,platform = WINDOWS -port 5558


//java -Dwebdriver.chrome.driver = D:\chromedriver.exe 
 //  -jar D:\JAR\selenium-server-standalone-2.42.2.jar 
  // -role webdriver -hub  http://10.30.217.157:4444/grid/register 
 //  -browser browserName = chrome, platform = WINDOWS -port 5557



package SeleDemos;
import org.testng.annotations.Test;
import java.net.MalformedURLException;
import java.util.concurrent.TimeUnit;
import java.net.URL;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Parameters;
public class TestNGGridDemo {
  WebDriver driver;
  public String URL;
  public String Node;
  protected ThreadLocal<RemoteWebDriver> threadDriver = null;
	
  @Parameters("browser")
  @BeforeTest
     public void launchapp(String browser) throws MalformedURLException
	{
	 
	      String URL = "http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms";
	    
	      if (browser.equalsIgnoreCase("chrome")) {
	         System.out.println(" Executing on CHROME");
	         DesiredCapabilities cap = DesiredCapabilities.chrome();
	         cap.setBrowserName("chrome");
	         String Node = "http://10.124.129.50:4444/wd/hub";
	         driver = new RemoteWebDriver(new URL(Node), cap);
	         driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
	        
	         
	         driver.navigate().to(URL);
	         driver.manage().window().maximize();
	      } else if (browser.equalsIgnoreCase("ie")) {
	         System.out.println(" Executing on IE");
	         DesiredCapabilities cap = DesiredCapabilities.chrome();
	         cap.setBrowserName("ie");
	         String Node = "http://10.124.129.50:4444/wd/hub";
	         driver = new RemoteWebDriver(new URL(Node), cap);
	         driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
	        
	         driver.navigate().to(URL);
	         driver.manage().window().maximize();
	      } else {
	         throw new IllegalArgumentException("The Browser Type is Undefined");
	      }
	  }
	
	@Test
  public void f() throws InterruptedException 
  {
		  driver.findElement(By.id("username")).sendKeys("cttest_admin");
		  Thread.sleep(2000);
		  driver.findElement(By.id("password")).sendKeys("cms123");
		  Thread.sleep(2000);
		  driver.findElement(By.id("submit-button")).click();
		  Thread.sleep(2000);	
  }
	
	@AfterTest
	public void AfterTest()
	{
		driver.close();
	}
}
