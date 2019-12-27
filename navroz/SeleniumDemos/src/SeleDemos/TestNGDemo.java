package SeleDemos;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.testng.annotations.AfterTest;

public class TestNGDemo {
	WebDriver driver;
	
  @BeforeTest
  public void beforeTest() {
	  System.setProperty("webdriver.chrome.driver","D:\\navroz\\eclipse-jee-oxygen-3a-win32-x86_64\\soft\\chromedriver_win32\\chromedriver.exe");
	  driver = new ChromeDriver();
	  driver.manage().timeouts().implicitlyWait(10,TimeUnit.SECONDS);
	  driver.manage().window().maximize();
	  driver.navigate().to(" http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");
  }
	
  @Test
  public void f() throws InterruptedException, IOException {
	  driver.findElement(By.id("username")).sendKeys("cttest_admin");
	  Thread.sleep(2000);
	  driver.findElement(By.id("password")).sendKeys("cms123");
	  Thread.sleep(2000);
	  driver.findElement(By.id("submit-button")).click();
	  Thread.sleep(2000);
	  File screenshot = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
	  FileUtils.copyFile(screenshot, new File("D:\\navroz\\screenshot.jpg"));
	  Thread.sleep(2000);
  }
  
  @AfterTest
  public void afterTest() {
	  driver.close();
  }

}
