import org.openqa.selenium.chrome.*;
import java.util.concurrent.TimeUnit;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
public class Demo1 {

	public static void main(String[] args) throws InterruptedException
	{
		System.setProperty("webdriver.chrome.driver","D:\\navroz\\eclipse-jee-oxygen-3a-win32-x86_64\\soft\\chromedriver_win32\\chromedriver.exe");
		WebDriver driver = new ChromeDriver();
		driver.manage().window().fullscreen();
		driver.get(" http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");
		driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
		driver.findElement(By.id("username")).sendKeys("Cttest_ContentManager");
		Thread.sleep(3000);
		driver.findElement(By.id("password")).sendKeys("cms123");
		Thread.sleep(3000);
		driver.findElement(By.id("submit-button")).click();
		Thread.sleep(9000);
		driver.findElement(By.className("foundation-layout-table-cellwrapper")).click();
		Thread.sleep(3000);
		driver.navigate().back();
		Thread.sleep(3000);
		driver.navigate().forward();
		Thread.sleep(3000);
		driver.close();
	}
}
