"""Direct, server-side dispatch of an already authorized admin announcement."""
from firebase_admin import firestore, get_app, initialize_app
from firebase_functions import firestore_fn, options, params

from sync import process_announcement_request

RUNTIME_SERVICE_ACCOUNT = params.StringParam("KOVA_RUNTIME_SERVICE_ACCOUNT")


def process_admin_event(event):
    change = event.data
    if change is None or not change.after.exists:
        return
    command = change.after.to_dict() or {}
    # Ignore our own running/completed writes. The worker also claims the current
    # document version, so a simultaneous Bridge run cannot claim it twice.
    if command.get("status") != "requested":
        return
    if command.get("action") != "announcement" or command.get("source") != "changelog":
        return
    try:
        get_app()
    except ValueError:
        initialize_app()
    process_announcement_request(firestore.client())


@firestore_fn.on_document_written(
    document="adminCommands/announcement",
    region="europe-west1",
    service_account=RUNTIME_SERVICE_ACCOUNT,
    memory=options.MemoryOption.MB_256,
    cpu="gcf_gen1",
    min_instances=0,
    max_instances=1,
    concurrency=1,
    timeout_sec=540,
)
def dispatch_admin_announcement(event: firestore_fn.Event[firestore_fn.Change]):
    process_admin_event(event)
