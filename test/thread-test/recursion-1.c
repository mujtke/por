
int X = 1;

int f(int a) {
	
	int c = f(a + 1);

	return c;
}

int main() {

	int b = f(X);

	return 0;
}
