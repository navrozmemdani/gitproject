//java -jar selenium-server-standalone-2.30.0.jar -role hub 
//java -jar selenium-server-standalone-3.4.0.jar -role webdriver -hub http://192.168.1.3:4444/grid/register -port 5566

package SeleDemos;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.*;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.util.concurrent.TimeUnit;
import java.io.File;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;

public class ScreenShotHubNodeDemo {
   public WebDriver driver;
   public String URL, Node;
   protected ThreadLocal<RemoteWebDriver> threadDriver = null;
   
   @Parameters("browser")
   @BeforeTest
   public void launchapp(String browser) throws MalformedURLException {
      String URL = "http://www.calculator.net";
      
      if (browser.equalsIgnoreCase("firefox")) {
         System.out.println(" Executing on FireFox");
         String Node = "http://10.112.66.52:5555/wd/hub";
         DesiredCapabilities cap = DesiredCapabilities.firefox();
         cap.setBrowserName("firefox");
         driver = new RemoteWebDriver(new URL(Node), cap);
         
         // Puts an Implicit wait, Will wait for 10 seconds
         // before throwing exception
         driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
         
         // Launch website
         driver.navigate().to(URL);
         driver.manage().window().maximize();
      } else {
         throw new IllegalArgumentException("The Browser Type is Undefined");
      }
   }
   
   @Test
   public void calculatepercent() throws IOException {
      // Click on Math Calculators
      driver.findElement(By.xpath(".//*[@id = 'menu']/div[3]/a")).click();
      
      // Click on Percent Calculators
      driver.findElement(By.xpath(".//*[@id = 'menu']/div[4]/div[3]/a")).click();
      
      // Make use of augmented Driver to capture Screenshots.
      WebDriver augmentedDriver = new Augmenter().augment(driver);
      File screenshot = ((TakesScreenshot)augmentedDriver).getScreenshotAs(OutputType.FILE);
      FileUtils.copyFile(screenshot, new File("D:\\screenshots\\remotescreenshot1.jpg"));

      // Screenshot would be saved on the system where the script is executed and NOT on remote machine.
      
      // Enter value 10 in the first number of the percent Calculator
      driver.findElement(By.id("cpar1")).sendKeys("10");
      
      // Enter value 50 in the second number of the percent Calculator
      driver.findElement(By.id("cpar2")).sendKeys("50");
      
      // Click Calculate Button
      driver.findElement(By.xpath(".//*[@id = 'content']/table/tbody/tr/td[2]/input")).click();
      
      // Get the Result Text based on its xpath
      String result = driver.findElement(By.xpath(".//*[@id = 'content']/p[2]/span/font/b")).getText();

      // Print a Log In message to the screen
      System.out.println(" The Result is " + result);
      
      
      if(result.equals("5")) {
         System.out.println(" The Result is Pass");
      } else {
         System.out.println(" The Result is Fail");
      }
   }
   
   @AfterTest
   public void closeBrowser() {
      driver.quit();
   }
}