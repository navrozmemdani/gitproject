package SeleDemos;

import java.io.File;

import java.io.IOException;

import java.util.concurrent.TimeUnit;

import java.awt.*;

import org.apache.commons.io.FileUtils;

import org.openqa.selenium.*;

import org.openqa.selenium.WebDriver;

import org.openqa.selenium.chrome.ChromeDriver;

import org.openqa.selenium.By;

import org.monte.media.math.Rational;

import org.monte.media.Format;

import org.monte.screenrecorder.ScreenRecorder;

import static org.monte.media.AudioFormatKeys.*;

import static org.monte.media.VideoFormatKeys.*;

public class ScreenRecoDemo {

  private static ScreenRecorder screenRecorder;

  public static void main(String[] args) throws IOException, AWTException, InterruptedException {

   GraphicsConfiguration gconfig = GraphicsEnvironment

     .getLocalGraphicsEnvironment()

     .getDefaultScreenDevice()

     .getDefaultConfiguration();

   screenRecorder = new ScreenRecorder(gconfig,

     new Format(MediaTypeKey, MediaType.FILE, MimeTypeKey, MIME_AVI),

     new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey,

      ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,

      CompressorNameKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,

      DepthKey, (int)24, FrameRateKey, Rational.valueOf(15),

      QualityKey, 1.0f,

      KeyFrameIntervalKey, (int) (15 * 60)),

     new Format(MediaTypeKey, MediaType.VIDEO,

      EncodingKey,"black", FrameRateKey, Rational.valueOf(30)), null);

  System.setProperty("webdriver.chrome.driver","D:\\\\navroz\\\\eclipse-jee-oxygen-3a-win32-x86_64\\\\soft\\\\chromedriver_win32\\\\chromedriver.exe");

 WebDriver driver = new ChromeDriver();

   screenRecorder.start();

   driver.manage().window().maximize();

   driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

   driver.navigate().to("http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");

   driver.findElement(By.id("username")).sendKeys("cttest_admin");

   Thread.sleep(2000);

   driver.findElement(By.id("password")).sendKeys("cms123");

   Thread.sleep(2000);

   driver.findElement(By.id("submit-button")).click();

   Thread.sleep(2000);

   File screenshot = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);

   FileUtils.copyFile(screenshot, new File("D:\\navroz\\screenshots2.jpg"));

   driver.close();

   screenRecorder.stop();

  }

}