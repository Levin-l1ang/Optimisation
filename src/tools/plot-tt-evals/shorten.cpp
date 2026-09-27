#include <fstream>
#include <string>

using namespace std;

int main(void)
{
	ifstream file("all.txt");
	ofstream out("sall.txt");
	string line;
	int val;

	string A("Weighted activity slack: ");
	string B("Weighted driving time sum of all OD pairs: ");

	while (!file.eof())
	{
		
		getline(file,line);
		size_t pos = line.find(A);
		if (pos != string::npos)
		{
			line = line.substr(pos + A.length());
			out<<line<<"\t";
		}
		else
		{
			pos = line.find(B);
			if (pos != string::npos)
			{
			line = line.substr(pos + B.length());
			out<<line<<"\n";
			}
		}
	}

	return 0;
}
