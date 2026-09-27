import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import processing.core.PApplet;

public class MapGUI {
	
	private static Map map;
	private static String path;
	
	public static void createAndShowUI(){
		JFrame frame = new JFrame();
		Dimension dim = new Dimension(Environment.getX(), Environment.getY());
		frame.setSize(dim);
		frame.setMinimumSize(dim);
		frame.setMaximumSize(dim);
		frame.setPreferredSize(dim);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		map = new Map(path, Environment.getX(), Environment.getY());
		map.init();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(map);
		frame.add(panel);
		frame.pack();
		frame.setVisible(true);
	}
	
	public static void main(String[] args) {
		path = args[0];
		java.awt.EventQueue.invokeLater(new Runnable() {
         public void run() {
            createAndShowUI();
         }
      });
   }
}
