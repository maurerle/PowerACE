package tools.other;

import java.util.Map;
import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.PowerMarkets;
import simulations.initialization.Settings;

/**
 * Mail handling.
 *
 * @author Frank Sensfuss
 */
public final class Mail {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Mail.class.getName());

	public static boolean mailBreakPoint() {
		try {
			tools.other.Mail.postMail(PowerMarkets.getMailAddress(), "Breakpoint hit",
					"PowerACE hit breakpoint", "simpace@host.domain");
		} catch (final MessagingException e) {
			logger.error("Mail could not be sent.", e);
		}
		return true;
	}

	private static String getComputerName() throws Exception	{
	    Map<String, String> env = System.getenv();
	    if (env.containsKey("COMPUTERNAME"))
	        return env.get("COMPUTERNAME");
	    else if (env.containsKey("HOSTNAME"))
	        return env.get("HOSTNAME");
	    else
	        return "Unknown Computer";
	}
	
	public static void mailSimEnd(Tuple<String, String> totalTime) {
		try {
			final String name = Mail.getComputerName();
			tools.other.Mail.postMail(PowerMarkets.getMailAddress(), "PowerACE simulation finished",
					"PowerACE run " + Settings.getLogPathName() + " finished (total time: "
							+ totalTime.getX() + "; average time: " + totalTime.getY() + ") on machine "
							+name+".", "simpace@host.domain");
		} catch (final MessagingException e) {
			logger.error("Mail could not be sent.", e);
		} catch (final Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * sends an email
	 */
	public static void postMail(String recipient, String subject, String message, String from)
			throws MessagingException {
		final Properties props = new Properties();
		props.put("mail.smtp.host", "smtp.host.domain");
		final Session session = Session.getDefaultInstance(props);
		final Message msg = new MimeMessage(session);
		final InternetAddress addressFrom = new InternetAddress(from);
		msg.setFrom(addressFrom);
		final InternetAddress addressTo = new InternetAddress(recipient);
		msg.setRecipient(Message.RecipientType.TO, addressTo);
		msg.setSubject(subject);
		msg.setContent(message, "text/plain");
		Transport.send(msg);

	}
}