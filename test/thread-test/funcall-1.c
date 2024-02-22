
int f() {

	return 4;

}

int main() {

	int x = 1, y = 0;

	while (x < 3 && y >= 0) {
		x = x + 1;
		while (y < 2) {
			y = y + 1;
			int b = f();
		}
	}

	return 0;
}
