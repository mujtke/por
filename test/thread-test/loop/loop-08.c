
int x = 1, y = 0;

int main() {

// 	while (x < 3) {
// 		x = x + 1;
// 	}

// 	while (x < 3 && y < 2) {
// 		x = x + 1;
// 		y = y + 1;
// 	}

	while (x < 3) {
		x = x + 1;
		while (y < 2) {
			y = y + 1;
		}
	}

	return 0;
}
