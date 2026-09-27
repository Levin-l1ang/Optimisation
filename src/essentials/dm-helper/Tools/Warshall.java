/**
 * Contains the Warshall-algorithm for transitive closure
 * as static methods
 *
 */
public class Warshall {

	public static boolean isReachable(long[][] m, int i, int j) {
		return ((i == j) || (((m[i][j / 64] >>> (j % 64)) & 1L) == 1L));
	}

	public static void setReachable(long[][] m, int i, int j, boolean b) {
		long mask = 1L << (j % 64);
		if (b) m[i][j / 64] |= mask;
		else m[i][j / 64] &= ~mask;
	}

	public static void applyTransitiveClosure(long[][] m) {
		int m1 = m.length;
		if (m1 == 0) {
		    // We don't have any matrix
		    return;
        }
		int m2 = m[0].length;
		for (int k = 0; k < m1; k++) {
			int k1 = k / 64;
			int k2 = k % 64;
			for (int i = 0; i < m1; i++)
				if (((m[i][k1] >>> k2) & 1L) == 1L)
					for (int j = 0; j < m2; j++)
						m[i][j] |= m[k][j];
		}
	}


	public static void main(String[] args) throws InterruptedException
	{
		for (int i = 0; i < 3*64; i++)
		{
			for (int j = 0; j < 3*64; j++)
			{
				if (i == j) continue;
				for (int k = 0; k < 3*64; k++)
				{
					if (i == k) continue;
					if (j == k) continue;
					long[][] m = new long[3*64][3];
					setReachable(m, i, j, true);
					setReachable(m, j, k, true);
					if (!isReachable(m, i, j)) System.out.println("1: " + i + " - " + j + " - " + k);
					if (!isReachable(m, j, k)) System.out.println("2: " + i + " - " + j + " - " + k);
					if (isReachable(m, i, k)) System.out.println("3: " + i + " - " + j + " - " + k);
					applyTransitiveClosure(m);
					if (!isReachable(m, i, j)) System.out.println("4: " + i + " - " + j + " - " + k);
					if (!isReachable(m, j, k)) System.out.println("5: " + i + " - " + j + " - " + k);
					if (!isReachable(m, i, k)) System.out.println("6: " + i + " - " + j + " - " + k);
					if (isReachable(m, k, i)) System.out.println("7: " + i + " - " + j + " - " + k);
					if (isReachable(m, k, j)) System.out.println("8: " + i + " - " + j + " - " + k);
					if (isReachable(m, j, i)) System.out.println("9: " + i + " - " + j + " - " + k);
					//Thread.sleep(100);
				}

			}
		}
	}


}
