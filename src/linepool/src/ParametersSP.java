import net.lintim.util.Config;

public class ParametersSP extends Parameters{
    private final int numberShortestPaths;
    public ParametersSP(Config config) {
        super(config);
        numberShortestPaths = config.getIntegerValue("lpool_number_shortest_paths");
    }

    public int getNumberShortestPaths() {
        return numberShortestPaths;
    }

    @Override
    public boolean shouldAddShortestPaths() {
        return false;
    }

    @Override
    public double getRatioSp() {
        return 0;
    }
}
