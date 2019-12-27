package StepDefination;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class stepdefine {
	WebDriver driver;
@Given("open browser and web url")
public void open_browser_and_web_url() {
	 System.setProperty("webdriver.chrome.driver","D:\\\\navroz\\\\eclipse-jee-oxygen-3a-win32-x86_64\\\\soft\\\\chromedriver_win32\\\\chromedriver.exe");
	    driver = new ChromeDriver();
	   driver.navigate().to("http://freestyle-qaelasti-1gwriex3gbvtz-434254207.us-east-1.elb.amazonaws.com:4502/sites.html/content/freestyle-cms");
    throw new cucumber.api.PendingException();
}

@When("provide credentials")
public void provide_credentials() throws InterruptedException {
	   driver.findElement(By.id("username")).sendKeys("cttest_admin");
	   Thread.sleep(2000);
	   driver.findElement(By.id("password")).sendKeys("cms123");
	   Thread.sleep(2000);
    throw new cucumber.api.PendingException();
}

@Then("click on submit and close")
public void click_on_submit_and_close() throws InterruptedException {
	driver.findElement(By.id("submit-button")).click();
	Thread.sleep(2000);
	 driver.close();
    throw new cucumber.api.PendingException();
}


}
