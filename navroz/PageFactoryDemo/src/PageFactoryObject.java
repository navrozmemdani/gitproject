import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.CacheLookup;
import org.openqa.selenium.support.FindBy;

public class PageFactoryObject {
	WebDriver driver;
	@FindBy(id="username")
	@CacheLookup
	WebElement username;
	
	@FindBy(id="password")
	@CacheLookup
	WebElement password;
	
	@FindBy(id="submit-button")
	@CacheLookup
	WebElement submit;
	
	public PageFactoryObject(WebDriver driver) {
		this.driver = driver;
	}	
	
	
	public void typeusername(String uname)
	{
		username.sendKeys(uname);
	}
	
	public void typepassword(String pass)
	{
		password.sendKeys(pass);
	}
	
	public String getpagetitle()
	{
		return driver.getTitle();
	}


	public void typesubmit() {
		submit.click();	
	}
}
