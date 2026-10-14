package graph.structures.digraph.activation;

import java.util.Optional;

public class Activation
{
	private static ThreadLocal<Optional<Activation>> activation = ThreadLocal.withInitial(() -> Optional.empty());

	private ActivationFunction activationFunction;

	private Activation()
	{
		this.activationFunction = new DefaultActivationFunction();
	}

	public static Activation getActivation()
	{
		if (activation.get().isEmpty())
		{
			activation.set(Optional.of(new Activation()));
		}
		return activation.get().get();
	}

	public ActivationFunction getActivationFunction()
	{
		return activationFunction;
	}

	public void setActivationFunction(ActivationFunction activationFunction)
	{
		this.activationFunction = activationFunction;
	}
}