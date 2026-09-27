package net.lintim.fileHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class OutputFile extends File{
	
	private static final long serialVersionUID = 1L;
	
	BufferedWriter out;

	public OutputFile(String pathname) {
		super(pathname);
		if(!this.exists()){
			try {
				this.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			out = new BufferedWriter(new FileWriter(this, true));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void appendln(String line){
		try {
			out.write(line + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void closeWriter(){
		try {
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
