
import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.PageFactory;

public class PagefactoryTest {
		@Test
		public void test() {
			System.setProperty("webdriver.chrome.driver","D:\\navroz\\eclipse-jee-oxygen-3a-win32-x86_64\\soft\\chromedriver_win32\\chromedriver.exe");
			WebDriver driver = new ChromeDriver();
			PageFactoryObject obj = PageFactory.initElements(driver, PageFactoryObject.class);
			driver.manage().window().maximize();
			driver.get(" http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");
			driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
			obj.typeusername("cttest_admin");
			obj.typepassword("cms123");
			obj.typesubmit();
			driver.close();
		}
		
		@Test
		public void verifytitle()
		{
			System.setProperty("webdriver.chrome.driver","D:\\navroz\\eclipse-jee-oxygen-3a-win32-x86_64\\soft\\chromedriver_win32\\chromedriver.exe");
			WebDriver driver = new ChromeDriver();
			PageFactoryObject obj = PageFactory.initElements(driver, PageFactoryObject.class);
			driver.manage().window().maximize();
			driver.get(" http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");
			driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
			String pgTitle = obj.getpagetitle();
			assertTrue(pgTitle.equals("QA | AEM Sign In"));
			driver.close();
		}
}















