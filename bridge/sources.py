from dataclasses import dataclass
from typing import Callable, Protocol


class ScheduleSource(Protocol):
    """Server-side adapter boundary for an authorized future API.

    Return normalized organization/event dictionaries using the snapshot contract.
    Credentials stay on the server; source failures must propagate to health status.
    """
    def organizations(self) -> list[dict]: ...
    def fetch(self, organization: dict) -> tuple: ...


@dataclass
class PublicCalendarSource:
    discover: Callable
    fetch_public: Callable

    def organizations(self) -> list[dict]:
        return self.discover()

    def fetch(self, organization: dict) -> tuple:
        return self.fetch_public(organization)
