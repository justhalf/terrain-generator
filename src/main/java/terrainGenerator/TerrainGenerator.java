package terrainGenerator;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class TerrainGenerator extends JPanel{
	
	public JPanel panel;
	public JButton generateButton;
	public BufferedImage img;
	public Random random;
	
	/** The map size */
	public int size;
	/** The size of each tile */
	public int blockSize;
	/**
	 * Threshold to consider which heights are considered land.
	 * Imagine this as the world water height.
	 * This is not supposed to be set directly, use {@link #landRatio} instead.
	 */
	public double threshold;
	/** The desired land to overall ratio */
	public double landRatio;
	/** The zoom factor, higher means closer */
	public double zoom;
	/** The location in the world map */
	public int rowOffset, colOffset;
	
	/** Internal terrain generation parameter */
	public int octave;
	
	private boolean run;
	private int inc;
	
	private final int IDEAL_OCTAVE = 3;
	private final double IDEAL_LAND_RATIO = 0.38196601125; // 1-1/PHI, where PHI is the golden ratio

	public TerrainGenerator(int size, int blockSize, double desiredLandRatio){
		random = new Random();
		random.setSeed(System.currentTimeMillis());
		
		generateTerrain();
		this.run = false;
		this.inc = 1;
		this.size = size;
		this.octave = IDEAL_OCTAVE;
		this.blockSize = blockSize;
		this.zoom = 10;
		this.landRatio = desiredLandRatio;
		this.threshold = thresholdForLandRatio(landRatio);
	    img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
	    panel = new JPanel() {
	        @Override
	        public void paint(Graphics g) {
	            g.drawImage(img, 0, 0, null);
	        }
	    };
	    panel.setPreferredSize(new Dimension(size, size));
	    
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		add(panel);
		
		generateButton = new JButton("Generate!");
		generateButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				generateTerrain();
				threshold = thresholdForLandRatio(landRatio);
				paint();
			}
		});
		Dimension defaultSize = generateButton.getMinimumSize();
		generateButton.setPreferredSize(new Dimension(100, defaultSize.height));
		
		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		
		final JButton startButton = new JButton("Start");
		startButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				if(run){
					startButton.setText("Start");
					run = false;
					return;
				}
				startButton.setText("Stop");
				run = true;
			    new Thread(new Runnable(){
					public void run() {
						int i = (int)(threshold*100);
						while(run){
							threshold = i/100.0;
							paint();
							try{
								Thread.sleep(20);
							} catch(InterruptedException e){}
							i+=inc;
							if(i==101) inc = -1;
							else if(i==-1) inc = 1;
						}
					}
			    	
			    }).start();
			}
		});
		buttons.add(generateButton);
		buttons.add(startButton);
		add(Box.createRigidArea(new Dimension(0,10)));
		add(buttons);

		JPanel generateButtons = new JPanel();
		generateButtons.setLayout(new BoxLayout(generateButtons, BoxLayout.X_AXIS));
		final JButton generateBestButton = new JButton("Generate Best");
		generateBestButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				generateTerrain();
				threshold = thresholdForLandRatio(IDEAL_LAND_RATIO);
				paint();
			}
		});
		generateButtons.add(generateBestButton);

		final JButton bestButton = new JButton("Best");
		bestButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				threshold = thresholdForLandRatio(IDEAL_LAND_RATIO);
				paint();
			}
		});
		generateButtons.add(bestButton);
		add(generateButtons);
		
	    paint();
	    
	    setVisible(true);
	}
	
	/**
	 * Returns the threshold required to get the desired land ratio
	 * @param landRatio
	 * @return
	 */
	private double thresholdForLandRatio(double landRatio){
		double low = 0.0;
		double high = 1.0;
		double mid = (high+low)/2;
		double landTileRatio = landTileRatio(mid);
		while(high-low > 1e-8){
			if(landTileRatio < landRatio) high = mid;
			else low = mid;
			mid = (high+low)/2;
			landTileRatio = landTileRatio(mid);
		}
		return mid;
	}
	
	/**
	 * Calculate land ratio for a given threshold
	 * @param threshold
	 * @return
	 */
	private double landTileRatio(double threshold){
		int landTile = 0;
		int totalTile = 0;
        for(int row=0; row<size/blockSize; row++){
        	for(int col=0; col<size/blockSize; col++){
        		double height = getHeightAt((row+rowOffset)/10.0, (col+colOffset)/10.0);
        		if(height > threshold) landTile++;
        		totalTile++;
        	}
        }
        return 1.0*landTile/totalTile;
	}
	
	/**
	 * Pick a point in the world map
	 */
	public void generateTerrain(){
        rowOffset = 500 + random.nextInt(500);
        colOffset = 500 + random.nextInt(500);
	}
	
	/**
	 * Code adapted from http://www.dreamincode.net/forums/topic/66480-perlin-noise/
	 * @param x
	 * @param y
	 * @return
	 */
	private double baseNoise(double x, double y){
		int n = (int)x+57*(int)y;
		n = (n<<13)^n;
		int nn=(n*(n*n*60493+19990303)+1376312589)&0x7fffffff;
		return 1.0-((double)nn/1073741824.0);
	}
	
	private double interpolate(double a, double b, double x){
		double ft = x*Math.PI;
		double f = (1-Math.cos(ft))*0.5;
		return a*(1-f)+b*f;
	}
	
	private double noise(double x, double y){
		double floorx = (double)((int)x);
		double floory = (double)((int)y);
		double s,t,u,v;
		s = baseNoise(floorx, floory);
		t = baseNoise(floorx+1, floory);
		u = baseNoise(floorx, floory+1);
		v = baseNoise(floorx+1, floory+1);
		double int1 = interpolate(s,t,x-floorx);
		double int2 = interpolate(u,v,x-floorx);
		return interpolate(int1, int2, y-floory);
	}
	
	/**
	 * Returns the height at specified point (normalized into [0,1])
	 * @param x
	 * @param y
	 * @return
	 */
	private double getHeightAt(double x, double y){
		double result = 0;
		for(int i=0; i<octave; i++){
			double frequency = Math.pow(2,i);
			double amplitude = Math.pow(0.5, i);
			result += noise(x*frequency, y*frequency)*amplitude;
		}
		if(result > 1) result = 1;
		if(result < -1) result = -1;
		return (1+result)/2;
	}
	
	/**
	 * Draw the terrain using current settings
	 */
	public void paint(){
	    Graphics2D g = img.createGraphics();
	    g.setPaint(new Color(255, 255, 255));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for(int row=0; row<size/blockSize; row++){
        	for(int col=0; col<size/blockSize; col++){
        		double height = getHeightAt((row+rowOffset)/zoom, (col+colOffset)/zoom);
        		boolean isLand = (height > threshold);
        		int red, grn, blu;
        		if(isLand){
	        		red = 30 + (int)(height*random.nextInt(15));
	        		grn = 100 + (int)(height*random.nextInt(42));
	        		blu = 20 + (int)(height*random.nextInt(15));
        		} else {
        			red = 20 + (int)(height*random.nextInt(40));
        			grn = 40 + (int)(height*random.nextInt(40));
        			blu = 215 + (int)(height*random.nextInt(40));
        		}
        		g.setPaint(new Color(red, grn, blu));
        		g.fillRect(row*blockSize, col*blockSize, blockSize, blockSize);
        	}
        }
        panel.repaint();
	}
	
	public static void main(String[] args){
		JFrame frame = new JFrame("Terrain Generator");
		final TerrainGenerator generator = new TerrainGenerator(300, 5, 0.5);
		frame.add(generator);
	    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	    frame.pack();
	    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
	    frame.setLocation(screenSize.width/2, screenSize.height/2);
	    frame.setVisible(true);
	}
}
