import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.rsbot.event.events.ServerMessageEvent;
import org.rsbot.event.listeners.PaintListener;
import org.rsbot.event.listeners.ServerMessageListener;
import org.rsbot.script.GEItemInfo;
import org.rsbot.script.Script;
import org.rsbot.script.ScriptManifest;
import org.rsbot.script.Skills;

/**
 * A. Humidifier (Allometry Humidifier)
 * 
 * This script is designed for RuneDev and is intended for filling vials with
 * the lunar humidify spell at any bank, including the grand exchange.
 * 
 * Before starting this script, ensure that your bank is currently showing both
 * empty and filled vials on the same tab. This script will not look for your
 * bank items!
 * 
 * In addition, it is recommended that you sell your vials at or above market
 * price. This script is capable of fluctuating market prices and a steep
 * decrease in filled vial price would make this script non-profitable.
 * 
 * Copyright (c) 2010
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * @author allometry
 * @version 0.1
 * @since 0.1
 */

@ScriptManifest(authors = { "Allometry" }, category = "Magic", name = "A. Humidifier", description = "Allometry Humidifier", summary = "Fills vials with humidify spell", version = 0.1)
public class AHumidifier extends Script implements PaintListener, ServerMessageListener {
	private boolean isMonitorRunning = true, isRunning = false;
	
	private int emptyVialID = 229, filledVialID = 227;
	
	private int filledVialMarketPrice = 0;
	private int accumulatedHumidifyCasts = 0, accumulatedFilledVials = 0, accumulatedGold = 0;
	private int startingMagicEP = 0, startingMagicLevel = 0;
	
	private int humidifyCastsWidgetIndex = 0, vialsFilledWidgetIndex = 0, approxGoldAccumulatedWidgetIndex = 0;
	private int currentRuntimeWidgetIndex = 0, magicEPEarnedWidgetIndex = 0;
	
	private long startingTime = 0;
	
	private Image coinImage, cursorImage, drinkImage, sumImage, timeImage, weatherImage;
	private ImageObserver observer;
	
	private Monitor monitor = new Monitor();
	
	private NumberFormat numberFormatter = NumberFormat.getNumberInstance(Locale.US);
	
	private Scoreboard topLeftScoreboard, topRightScoreboard;
	
	private ScoreboardWidget humidifyCasts, vialsFilled, approxGoldAccumulated;
	private ScoreboardWidget currentRuntime, magicEPEarned;
	
	private String magicEPEarnedWidgetText = "";
	
	private Thread monitorThread;
	
	@Override
	public boolean onStart(Map<String,String> args) {
		try {
			log.info("Attempting to read image resources from the web...");
			
			coinImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/coins.png"));
			cursorImage = ImageIO.read(new URL("http://scripts.allometry.com/app/webroot/img/cursors/cursor-01.png"));
			drinkImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/drink.png"));
			sumImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/sum.png"));
			timeImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/time.png"));
			weatherImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/weather_rain.png"));
			
			log.info("Success! All image resources have been loaded...");
		} catch (IOException e) {
			log.warning("There was an issue trying to read the image resources from the web...");
		}
		
		try {
			log.info("Attempting to get the latest filled vial market price...");
			
			GEItemInfo vialItem = grandExchange.loadItemInfo(filledVialID);
			filledVialMarketPrice = vialItem.getMarketPrice();
			
			log.info("Success! The filled vial price is " + filledVialMarketPrice + "gp");
		} catch (Exception e) {
			log.warning("There was an issue trying to read the filled vial price from the web...");
		}
		
		try {
			//Assemble Top Left Widgets
			humidifyCasts = new ScoreboardWidget(weatherImage, "");
			vialsFilled = new ScoreboardWidget(drinkImage, "");
			approxGoldAccumulated = new ScoreboardWidget(coinImage, "");
			
			//Assemble Top Right Widgets 
			currentRuntime = new ScoreboardWidget(timeImage, "");
			magicEPEarned = new ScoreboardWidget(sumImage, "");
			
			//Assemble Top Left Scoreboard
			topLeftScoreboard = new Scoreboard(Scoreboard.TOP_LEFT, 128, 5);
			topLeftScoreboard.addWidget(humidifyCasts);
			humidifyCastsWidgetIndex = 0;
			
			topLeftScoreboard.addWidget(vialsFilled);
			vialsFilledWidgetIndex = 1;
			
			topLeftScoreboard.addWidget(approxGoldAccumulated);
			approxGoldAccumulatedWidgetIndex = 2;
			
			//Assemble Top Right Scoreboard
			topRightScoreboard = new Scoreboard(Scoreboard.TOP_RIGHT, 128, 5);
			topRightScoreboard.addWidget(currentRuntime);
			currentRuntimeWidgetIndex = 0;
			
			topRightScoreboard.addWidget(magicEPEarned);
			magicEPEarnedWidgetIndex = 1;
		} catch (Exception e) {
			log.warning("There was an issue creating the scoreboard...");
		}
		
		try {
			startingMagicEP = skills.getCurrentSkillExp(Skills.getStatIndex("Magic"));
			startingMagicLevel = skills.getCurrSkillLevel(Skills.getStatIndex("Magic"));
			
			startingTime = System.currentTimeMillis();
		} catch (Exception e) {
			log.warning("There was an issue instantiating some or all objects...");
		}
		
		monitorThread = new Thread(monitor);
		
		isRunning = true;
		
		return true;
	}
	
	@Override
	public int loop() {
		return 1;
	}
	
	@Override
	public void onFinish() {
		log.info("Stopping monitor thread...");
		
		//Gracefully stop monitorThread
		while(monitorThread.isAlive()) {
			isMonitorRunning = false;
			monitorThread.notifyAll();
		}
		
		log.info("Monitor thread stopped...");
		
		//Gracefully release thread and runnable objects
		monitorThread = null;
		monitor = null;
		
		return ;
	}

	@Override
	public void onRepaint(Graphics g2) {
		if(!isRunning) return ;
		
		Graphics2D g = (Graphics2D)g2;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		//Draw Custom Mouse Cursor
		g.drawImage(cursorImage, getMouseLocation().x - 16, getMouseLocation().y - 16, observer);
		
		//Draw Top Left Scoreboard
		topLeftScoreboard.getWidget(humidifyCastsWidgetIndex).setWidgetText(numberFormatter.format(accumulatedHumidifyCasts));
		topLeftScoreboard.getWidget(vialsFilledWidgetIndex).setWidgetText(numberFormatter.format(accumulatedFilledVials));
		topLeftScoreboard.getWidget(approxGoldAccumulatedWidgetIndex).setWidgetText("$" + numberFormatter.format(accumulatedGold));
		topLeftScoreboard.drawScoreboard(g);
		
		//Draw Top Right Scoreboard
		topRightScoreboard.getWidget(currentRuntimeWidgetIndex).setWidgetText(millisToClock(System.currentTimeMillis() - startingTime));
		topRightScoreboard.getWidget(magicEPEarnedWidgetIndex).setWidgetText(magicEPEarnedWidgetText);
		topRightScoreboard.drawScoreboard(g);
		
		//Draw Magic Progress Bar
		RoundRectangle2D progressBackground = new RoundRectangle2D.Float(
				Scoreboard.gameCanvasRight - 128,
				topRightScoreboard.getHeight() + 30,
				128,
				8,
				5,
				5);
		
		log("height " + topRightScoreboard.getHeight());
		
		Double percentToWidth = Math.floor(128 * (skills.getPercentToNextLevel(Skills.getStatIndex("Magic")) / 100));
		
		RoundRectangle2D progressBar = new RoundRectangle2D.Float(
				Scoreboard.gameCanvasRight - 128,
				topRightScoreboard.getHeight() + 31,
				percentToWidth.intValue(),
				7,
				5,
				5);
		
		g.setColor(new Color(0, 0, 0, 127));
		g.draw(progressBackground);
		
		g.setColor(new Color(0, 0, 200, 191));
		g.fill(progressBar);
		
		return ;
	}

	@Override
	public void serverMessageRecieved(ServerMessageEvent e) {
		return ;
	}
	
	/**
	 * Formats millisecond time into HH:MM:SS
	 * 
	 * @param milliseconds				milliseconds that should be converted into
	 * 									the HH:MM:SS format
	 * 									@see java.lang.System
	 * @return							formatted HH:MM:SS string
	 * @since 0.1
	 */
	private String millisToClock(long milliseconds) {
		long seconds = (milliseconds / 1000), minutes = 0, hours = 0;
		
		if (seconds >= 60) {
			minutes = (seconds / 60);
			seconds -= (minutes * 60);
		}
		
		if (minutes >= 60) {
			hours = (minutes / 60);
			minutes -= (hours * 60);
		}
		
		return (hours < 10 ? "0" + hours + ":" : hours + ":")
				+ (minutes < 10 ? "0" + minutes + ":" : minutes + ":")
				+ (seconds < 10 ? "0" + seconds : seconds);
	}
	
	/**
	 * Monitor class assembles and updates all experience points and levels gained. The
	 * class also maintains strings for the onRepaint method.
	 * 
	 * @author allometry
	 * @version 1.0
	 * @since 1.0
	 */
	public class Monitor implements Runnable {
		@Override
		public void run() {
			while(isMonitorRunning) {
				magicEPEarnedWidgetText = (skills.getCurrSkillLevel(Skills.getStatIndex("Magic")) != startingMagicLevel) ? "" + numberFormatter.format(skills.getCurrentSkillExp(Skills.getStatIndex("Magic")) - startingMagicEP) + " (+" + numberFormatter.format(skills.getCurrSkillLevel(Skills.getStatIndex("Magic")) - startingMagicLevel) + ")" : "" +  numberFormatter.format(skills.getCurrentSkillExp(Skills.getStatIndex("Magic")) - startingMagicEP);
			}
		}
	}
	
	/**
	 * Scoreboard is a class for assembling individual scoreboards with widgets
	 * in a canvas space.
	 * 
	 * @author allometry
	 * @version 1.0
	 * @since 1.0
	 */
	public class Scoreboard {
		public static final int TOP_LEFT = 1, TOP_RIGHT = 2, BOTTOM_LEFT = 3, BOTTOM_RIGHT = 4;
		public static final int gameCanvasTop = 25, gameCanvasLeft = 25, gameCanvasBottom = 309, gameCanvasRight = 487;

		private ImageObserver observer = null;

		private int scoreboardLocation, scoreboardX, scoreboardY, scoreboardWidth,
				scoreboardHeight, scoreboardArc;

		private ArrayList<ScoreboardWidget> widgets = new ArrayList<ScoreboardWidget>();
		
		/**
		 * Creates a new instance of Scoreboard.
		 * 
		 * @param scoreboardLocation	the location of where the scoreboard should be drawn on the screen
		 * 								@see Scoreboard.TOP_LEFT
		 * 								@see Scoreboard.TOP_RIGHT
		 * 								@see Scoreboard.BOTTOM_LEFT
		 * 								@see Scoreboard.BOTTOM_RIGHT
		 * @param width					the pixel width of the scoreboard
		 * @param arc					the pixel arc of the scoreboard rounded rectangle
		 * @since 1.0
		 */
		public Scoreboard(int scoreboardLocation, int width, int arc) {
			this.scoreboardLocation = scoreboardLocation;
			scoreboardHeight = 10;
			scoreboardWidth = width;
			scoreboardArc = arc;

			switch (scoreboardLocation) {
			case 1:
				scoreboardX = gameCanvasLeft;
				scoreboardY = gameCanvasTop;
				break;

			case 2:
				scoreboardX = gameCanvasRight - scoreboardWidth;
				scoreboardY = gameCanvasTop;
				break;

			case 3:
				scoreboardX = gameCanvasLeft;
				break;

			case 4:
				scoreboardX = gameCanvasRight - scoreboardWidth;
				break;
			}
		}
		
		/**
		 * Adds a ScoreboardWidget to the Scoreboard.
		 * 
		 * @param widget				an instance of a ScoreboardWidget containing an image
		 * 								and text
		 * 								@see ScoreboardWidget
		 * @return						true if the widget was added to Scoreboard
		 * @since 1.0
		 */
		public boolean addWidget(ScoreboardWidget widget) {
			return widgets.add(widget);
		}
		
		/**
		 * Gets a ScoreboardWidget by it's index within Scoreboard.
		 * 
		 * @param widgetIndex			the index of the ScoreboardWidget
		 * @return						an instance of ScoreboardWidget
		 * @since 1.0
		 */
		public ScoreboardWidget getWidget(int widgetIndex) {
			try {
				return widgets.get(widgetIndex);
			} catch (Exception e) {
				log.warning("Warning: " + e.getMessage());
				return null;
			}
		}
		
		/**
		 * Gets the Scoreboard widgets.
		 * 
		 * @return						an ArrayList filled with ScoreboardWidget's
		 */
		public ArrayList<ScoreboardWidget> getWidgets() {
			return widgets;
		}
		
		/**
		 * Draws the Scoreboard and ScoreboardWidget's to an instances of Graphics2D.
		 * 
		 * @param g						an instance of Graphics2D
		 * @return						true if Scoreboard was able to draw to the Graphics2D instance and false if it wasn't
		 * @since 1.0
		 */
		public boolean drawScoreboard(Graphics2D g) {
			try {
				if(scoreboardHeight <= 10) {
					for (ScoreboardWidget widget : widgets) {
						scoreboardHeight += widget.getWidgetImage().getHeight(observer) + 4;
					}
				}

				if (scoreboardLocation == 3 || scoreboardLocation == 4) {
					scoreboardY = gameCanvasBottom - scoreboardHeight;
				}

				RoundRectangle2D scoreboard = new RoundRectangle2D.Float(
						scoreboardX, scoreboardY, scoreboardWidth,
						scoreboardHeight, scoreboardArc, scoreboardArc);

				g.setColor(new Color(0, 0, 0, 127));
				g.fill(scoreboard);

				int x = scoreboardX + 5;
				int y = scoreboardY + 5;
				for (ScoreboardWidget widget : widgets) {
					widget.drawWidget(g, x, y);
					y += widget.getWidgetImage().getHeight(observer) + 4;
				}

				return true;
			} catch (Exception e) {
				return false;
			}
		}
		
		/**
		 * Returns the height of the Scoreboard with respect to it's contained ScoreboardWidget's.
		 * 
		 * @return						the pixel height of the Scoreboard
		 * @since 1.0 
		 */
		public int getHeight() {
			return scoreboardHeight;
		}
	}
	
	/**
	 * ScoreboardWidget is a container intended for use with a Scoreboard. Scoreboards contain
	 * an image and text, which are later drawn to an instance of Graphics2D.
	 * 
	 * @author allometry
	 * @version 1.0
	 * @since 1.0
	 * @see Scoreboard
	 */
	public class ScoreboardWidget {
		private ImageObserver observer = null;
		private Image widgetImage;
		private String widgetText;
		
		/**
		 * Creates a new instance of ScoreboardWidget.
		 * 
		 * @param widgetImage			an instance of an Image. Recommended size is 16x16 pixels
		 * 								@see java.awt.Image
		 * @param widgetText			text to be shown on the right of the widgetImage
		 * @since 1.0
		 */
		public ScoreboardWidget(Image widgetImage, String widgetText) {
			this.widgetImage = widgetImage;
			this.widgetText = widgetText;
		}
		
		/**
		 * Gets the widget image.
		 * 
		 * @return						the Image of ScoreboardWidget
		 * 								@see java.awt.Image
		 * @since 1.0
		 */
		public Image getWidgetImage() {
			return widgetImage;
		}
		
		/**
		 * Sets the widget image.
		 * 
		 * @param widgetImage			an instance of an Image. Recommended size is 16x16 pixels
		 * 								@see java.awt.Image
		 * @since 1.0
		 */
		public void setWidgetImage(Image widgetImage) {
			this.widgetImage = widgetImage;
		}
		
		/**
		 * Gets the widget text.
		 * 
		 * @return						the text of ScoreboardWidget
		 * @since 1.0
		 */
		public String getWidgetText() {
			return widgetText;
		}
		
		/**
		 * Sets the widget text.
		 * 
		 * @param widgetText			text to be shown on the right of the widgetImage
		 * @since 1.0
		 */
		public void setWidgetText(String widgetText) {
			this.widgetText = widgetText;
		}
		
		/**
		 * Draws the ScoreboardWidget to an instance of Graphics2D.
		 * 
		 * @param g						an instance of Graphics2D
		 * @param x						horizontal pixel location of where to draw the widget 
		 * @param y						vertical pixel location of where to draw the widget
		 * @since 1.0
		 */
		public void drawWidget(Graphics2D g, int x, int y) {
			g.setColor(Color.white);
			g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

			g.drawImage(widgetImage, x, y, observer);
			g.drawString(widgetText, x + widgetImage.getWidth(observer) + 4, y + 12);
		}
	}
}
