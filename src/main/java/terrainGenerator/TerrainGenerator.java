package terrainGenerator;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class TerrainGenerator extends JPanel{

	enum Method {
		PERLIN,
		MIDPOINT_DISPLACEMENT,
	}

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
	public int xOffset, yOffset;

	/** Internal terrain generation parameter */
	public int octave;

	public Method method;
	public double heights[][];
	public double roughness;

	private boolean run;
	private int inc;

	private int mouseX;
	private int mouseY;

	private final double MIN_ZOOM = 0.5;
	private final double MAX_ZOOM = 250;
	private final int IDEAL_OCTAVE = 3;
	private final double IDEAL_LAND_RATIO = 0.38196601125; // 1-1/PHI, where PHI is the golden ratio

	public TerrainGenerator(int desiredSize, int desiredBlockSize, double desiredLandRatio){
		random = new Random();
		random.setSeed(System.currentTimeMillis());

		generateTerrain();
		this.run = false;
		this.inc = 1;
		this.size = desiredSize;
		this.octave = IDEAL_OCTAVE;
		this.blockSize = desiredBlockSize;
		this.zoom = MIN_ZOOM;
		this.landRatio = desiredLandRatio;
		this.method = Method.PERLIN;
		this.threshold = thresholdForLandRatio(landRatio);
		this.roughness = 0.2;

		img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		panel = new JPanel() {
			@Override
			public void paint(Graphics g) {
				g.drawImage(img, 0, 0, null);
			}
		};
		panel.setPreferredSize(new Dimension(size, size));
		// For click events
		panel.addMouseListener(new MouseListener(){

			public void mouseClicked(MouseEvent e) {
				double curMouseX = e.getPoint().x;
				double curMouseY = e.getPoint().y;
				double worldX = (curMouseX/blockSize + xOffset)/zoom;
				double worldY = (curMouseY/blockSize + yOffset)/zoom;
				boolean isLand = getHeightAt(worldX, worldY) > threshold;
				if(isLand){
					JOptionPane.showMessageDialog(null, "Clicked on a land tile");
				} else {
					JOptionPane.showMessageDialog(null, "Clicked on a water tile");
				}
			}

			public void mousePressed(MouseEvent e) {
				mouseX = e.getPoint().x;
				mouseY = e.getPoint().y;
			}

			public void mouseReleased(MouseEvent e) {}

			public void mouseEntered(MouseEvent e) {}

			public void mouseExited(MouseEvent e) {}
		});
		// Enable dragging
		panel.addMouseMotionListener(new MouseMotionListener(){

			public void mouseDragged(MouseEvent e) {
				int curMouseX = e.getPoint().x;
				int curMouseY = e.getPoint().y;
				xOffset += (mouseX-curMouseX)/blockSize;
				yOffset += (mouseY-curMouseY)/blockSize;
				mouseX = curMouseX;
				mouseY = curMouseY;
				paint();
			}

			public void mouseMoved(MouseEvent e) {}
		});
		// Control zoom factor
		panel.addMouseWheelListener(new MouseWheelListener(){

			public void mouseWheelMoved(MouseWheelEvent e) {
				int zoomFactor = e.getWheelRotation();
				int x = e.getPoint().x/blockSize;
				int y = e.getPoint().y/blockSize;
				if(zoomFactor >= 1){
					double oldZoom = zoom;
					zoom *= 1.5;
					if(zoom > MAX_ZOOM){
						zoom = MAX_ZOOM;
					}
					xOffset = (int)(zoom*(x+xOffset)/oldZoom-x);
					yOffset = (int)(zoom*(y+yOffset)/oldZoom-y);
					paint();
				} else if(zoomFactor <= -1){
					double oldZoom = zoom;
					zoom /= 1.5;
					if(zoom < MIN_ZOOM){
						zoom = MIN_ZOOM;
					}
					xOffset = (int)(zoom*(x+xOffset)/oldZoom-x);
					yOffset = (int)(zoom*(y+yOffset)/oldZoom-y);
					paint();
				}
			}
		});

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		add(panel);

		JPanel optionPanel = new JPanel();
		optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.X_AXIS));

		JLabel optionLabel = new JLabel("Generation method:");
		optionPanel.add(optionLabel);

		JComboBox<String> options = new JComboBox<String>();
		options.addItem("Perlin");
		options.addItem("Midpoint Displacement");
		options.addItemListener(new ItemListener(){

			public void itemStateChanged(ItemEvent e) {
				if(ItemEvent.SELECTED == e.getStateChange()){
					if(e.getItem().equals("Perlin")){
						method = Method.PERLIN;
						threshold = thresholdForLandRatio(IDEAL_LAND_RATIO);
						paint();
					} else {
						method = Method.MIDPOINT_DISPLACEMENT;
						generateHeights();
						threshold = thresholdForLandRatio(IDEAL_LAND_RATIO);
						paint();
					}
				}
			}

		});
		optionPanel.add(options);

		add(optionPanel);

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
	 * Adjust height so that it will stay within the range [0,1]
	 * @param val
	 * @return
	 */
	public double normalize(double val){
		if(val>1) return 1;
		if(val<0) return 0;
		return val;
	}

	/**
	 * Generate the world dimension that should be generated
	 * @return
	 */
	private int getDimension(){
		int mapSize = (9*size/blockSize)/4;
		int result = 1;
		while(result < mapSize) result*=2;
		return result;
	}

	/**
	 * Generate the height map for the world
	 */
	public void generateHeights(){
		int dim = getDimension();
		heights = new double[dim][dim];
		for(int i=0; i<dim; i++){
			for(int j=0; j<dim; j++){
				heights[i][j] = 0;
			}
		}
		heights[0][0] = random.nextDouble();
		for(int curDim = dim; curDim > 1; curDim /= 2){
			int halfCurDim = curDim/2;
			// Diamond step
			for(int x=halfCurDim; x<dim; x+=curDim){
				for(int y=halfCurDim; y<dim; y+=curDim){
					int x1 = x-halfCurDim;
					int y1 = y-halfCurDim;
					int x2 = (x+halfCurDim)%dim;
					int y2 = (y+halfCurDim)%dim;
					heights[x][y] = randomAverage(
							curDim,
							heights[x1][y1],
							heights[x1][y2],
							heights[x2][y1],
							heights[x2][y2]
							);
				}
			}
			// Square step
			for(int x=0; x<dim; x+=halfCurDim){
				for(int y=(x/halfCurDim)%2==0?halfCurDim:0; y<dim; y+=curDim){
					int x1 = (x-halfCurDim+dim)%dim;
					int y1 = (y-halfCurDim+dim)%dim;
					int x2 = (x+halfCurDim)%dim;
					int y2 = (y+halfCurDim)%dim;
					heights[x][y] = randomAverage(
							curDim,
							heights[x1][y],
							heights[x2][y],
							heights[x][y1],
							heights[x][y2]
							);
				}
			}
		}
	}

	/**
	 * Take the average of ds and add some random value
	 * @param num
	 * @param ds
	 * @return
	 */
	private double randomAverage(double num, double... ds){
		double result = 0;
		for(int i=0; i<ds.length; i++){
			result += ds[i];
		}
		result = result/ds.length+displace(num);
		result = normalize(result);
		return result;
	}

	/**
	 * Add some random amount based on num
	 * @param num
	 * @return
	 */
	private double displace(double num){
		int dim = getDimension();
		double max = Math.E*num / dim;
		return (random.nextDouble()-0.45)*max;
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
		while(high-low > 1e-4){
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
		for(int x=0; x<size/blockSize; x++){
			for(int y=0; y<size/blockSize; y++){
				double height = getHeightAt((x+xOffset)/zoom, (y+yOffset)/zoom);
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
		xOffset = 500 + random.nextInt(500);
		yOffset = 500 + random.nextInt(500);
		if(method == Method.MIDPOINT_DISPLACEMENT){ 
			generateHeights();
		}
	}

	/**
	 * Code adapted from http://www.dreamincode.net/forums/topic/66480-perlin-noise/
	 * @param x
	 * @param y
	 * @return
	 */
	private double baseNoise(double x, double y){
		int n = (int)Math.round(x)+57*(int)Math.round(y);
		n = (n<<13)^n;
		int nn=(n*(n*n*60493+19990303)+1376312589) & 0x7fffffff;
		return 1.0-(nn/1073741824.0);
	}

	private double interpolate(double a, double b, double x){
		double f = (1-Math.cos(x*Math.PI))*0.5;
		return a*(1-f)+b*f;
	}

	private double noise(double x, double y){
		double floorx = (double)((int)x);
		double floory = (double)((int)y);
		if(x<0) floorx -= 1;
		if(y<0) floory -= 1;
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
		double result;
		if(method == Method.PERLIN){
			x /= 20;
			y /= 20;
			result = 0;
			int pow = 1;
			for(int i=0; i<octave; i++, pow*=2){
				result += noise(x*pow, y*pow)/pow;
			}
			result /= 2-(2.0/pow);
			result = (1+result)/2;
		} else { // method == Method.MIDPOINT_DISPLACEMENT
			int len = heights.length;
			int intX = (int)(x*2);
			int intY = (int)(y*2);
			intX = ((intX % len) + len) % len;
			intY = ((intY % len) + len) % len;
			result = heights[intX][intY];
		}
		return result;
	}

	/**
	 * Draw the terrain using current settings
	 */
	public void paint(){
		Graphics2D g = img.createGraphics();
		g.setPaint(new Color(255, 255, 255));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		for(int x=0; x<size/blockSize; x++){
			for(int y=0; y<size/blockSize; y++){
				double height = getHeightAt((x+xOffset)/zoom, (y+yOffset)/zoom);
				boolean isLand = (height > threshold);
				int red, grn, blu;
				if(isLand){
					red = 30 + (int)(height*random.nextInt(15));
					grn = 100 + (int)(height*random.nextInt(42));
					blu = 20 + (int)(height*random.nextInt(15));
//					red = 30 + (int)(height*15);
//					grn = 100 + (int)(height*42);
//					blu = 20 + (int)(height*15);
				} else {
					red = 20 + (int)(height*random.nextInt(40));
					grn = 40 + (int)(height*random.nextInt(40));
					blu = 215 + (int)(height*random.nextInt(40));
//					red = 20 + (int)(height*40);
//					grn = 40 + (int)(height*40);
//					blu = 215 + (int)(height*40);
				}
				g.setPaint(new Color(red, grn, blu));
				g.fillRect(x*blockSize, y*blockSize, blockSize, blockSize);
			}
		}
		panel.repaint();
	}

	public void markWithCircle(Graphics2D g){
		drawCircle(g, blockSize*(25*zoom-xOffset), blockSize*(25*zoom-yOffset), 20*zoom);
	}

	private void drawCircle(Graphics2D g, double x, double y, double radius){
		g.setPaint(new Color(255,0,0));
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
		g.fillOval((int)(x-radius), (int)(y-radius), (int)(2*radius), (int)(2*radius));
		g.setPaintMode();
	}

	public static void main(String[] args){
		JFrame frame = new JFrame("Terrain Generator");
		final TerrainGenerator generator = new TerrainGenerator(512, 2, 0.5);
		frame.add(generator);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		frame.setLocation((screenSize.width-frame.getSize().width)/2, (screenSize.height-frame.getSize().height)/2);
		frame.setVisible(true);
	}
}
