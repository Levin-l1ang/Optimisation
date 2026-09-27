package net.lintim.completor;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import jline.SimpleCompletor;
import net.lintim.fileHandler.DirFileFilter;

public class UseCompletor {

	private List<SimpleCompletor> useListCompletor;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public UseCompletor() {
		useListCompletor = new LinkedList();
		useListCompletor.add(new SimpleCompletor(new String[] { "use" }));

		// Read directory names of directory "datasets"
		DirFileFilter dirFileFilter = new DirFileFilter();

		String[] dirNames = (new File("datasets")).list(dirFileFilter);

		useListCompletor.add(new SimpleCompletor(dirNames));
	}

	public List<SimpleCompletor> getUseListCompletor() {
		return useListCompletor;
	}

	public void setUseListCompletor(List<SimpleCompletor> useListCompletor) {
		this.useListCompletor = useListCompletor;
	}
}
