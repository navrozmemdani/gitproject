package SeleDemos;

import org.apache.log4j.LogManager;

import org.apache.log4j.Logger;

import org.apache.log4j.xml.DOMConfigurator;

import java.util.concurrent.TimeUnit;

import org.openqa.selenium.*;

import org.openqa.selenium.chrome.ChromeDriver;



public class log4j_demo {

  static final Logger logger = LogManager.getLogger(log4j_demo.class.getName());



  public static void main(String[] args) throws InterruptedException {



   DOMConfigurator.configure("D:\\navroz\\eclipse-jee-oxygen-3a-win32-x86_64\\navroz\\SeleniumDemos\\src\\SeleDemos\\log4j.xml");

   logger.info("# # # # # # # # # # # # # # # # # # # # # # # # # # # ");

   logger.info("TEST Has Started");

   System.setProperty("webdriver.chrome.driver","D:\\\\navroz\\\\eclipse-jee-oxygen-3a-win32-x86_64\\\\soft\\\\chromedriver_win32\\\\chromedriver.exe");

   WebDriver driver = new ChromeDriver();

   driver.manage().window().maximize();

   driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

   driver.navigate().to("http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");

   logger.info("Open cms freestyle website");

   driver.findElement(By.id("username")).sendKeys("cttest_admin");

   logger.info("get username");

   Thread.sleep(2000);

   driver.findElement(By.id("password")).sendKeys("cms123");

   logger.info("get password");

   Thread.sleep(2000);

   driver.findElement(By.id("submit-button")).click();

   logger.info("click on submit");

   Thread.sleep(2000);

   logger.error("success!!!!");

   driver.close();

  }

}

