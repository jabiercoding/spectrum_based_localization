package spectrum.utils;

import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * A progress monitor that notifies when a task or subtask starts
 */
public class ConsoleProgressMonitor extends NullProgressMonitor {

	@Override
	public void setTaskName(String name) {
		System.out.println(name);
	}

	@Override
	public void subTask(String name) {
		System.out.println(name);
	}
}
