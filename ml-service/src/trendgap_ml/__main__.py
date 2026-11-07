import sys

import uvicorn
from pydantic import ValidationError

from trendgap_ml.app import create_app
from trendgap_ml.logging import configure_logging
from trendgap_ml.settings import Settings, format_validation_error


def main() -> int:
    try:
        settings = Settings()
    except ValidationError as exc:
        print(format_validation_error(exc), file=sys.stderr)
        return 1
    configure_logging(settings.log_level)
    uvicorn.run(
        create_app(),
        host="0.0.0.0",
        port=settings.port,
        log_config=None,
        log_level=settings.log_level.lower(),
        access_log=False,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
