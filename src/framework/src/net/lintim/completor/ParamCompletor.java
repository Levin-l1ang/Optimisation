package net.lintim.completor;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import jline.SimpleCompletor;
import net.lintim.input.FileInput;

public class ParamCompletor {
	private List<SimpleCompletor> paramListCompletor;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public ParamCompletor() {
		paramListCompletor = new LinkedList();
		paramListCompletor.add(new SimpleCompletor(new String[] { "param" }));
		paramListCompletor.add(new SimpleCompletor(new String[] { "write", "read" }));

		
		String[] completingCommands = new String[FileInput.getMapOfParameter().size()];
		Iterator<String> parameterIterator = FileInput.getMapOfParameter().keySet().iterator();
		int completingCommandsCounter = 0;
		while(parameterIterator.hasNext()){
			completingCommands[completingCommandsCounter++] = parameterIterator.next();
		}
		paramListCompletor.add(new SimpleCompletor(completingCommands));
	}

	public List<SimpleCompletor> getParamListCompletor() {
		return paramListCompletor;
	}

	public void setParamListCompletor(List<SimpleCompletor> paramListCompletor) {
		this.paramListCompletor = paramListCompletor;
	}

}
