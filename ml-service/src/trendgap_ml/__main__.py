import sys

from pydantic import ValidationError

from trendgap_ml.settings import Settings, format_validation_error


def main() -> int:
    try:
        Settings()
    except ValidationError as exc:
        print(format_validation_error(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
