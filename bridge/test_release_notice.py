from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from google.cloud.exceptions import Conflict

import release_notice


NOTICE = {
    "id": "kova-20261009-233",
    "version": "2.3.3",
    "title": "Kova Companion 2.3.3",
    "body": "Kalenderen vises uten overlapping på iPhone.",
}


class ReleaseNoticeTests(unittest.TestCase):
    def test_validates_reviewed_notice_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "notice.json"
            path.write_text(json.dumps(NOTICE), encoding="utf-8")
            self.assertEqual(release_notice.load_notice(path), NOTICE)
            path.write_text(json.dumps({**NOTICE, "audience": "all"}), encoding="utf-8")
            with self.assertRaises(ValueError):
                release_notice.load_notice(path)

    @patch.object(release_notice.requests, "get")
    def test_does_not_send_before_version_is_live(self, get):
        get.return_value.text = "Webapp · Android og iPhone · 2.3.2"
        with self.assertRaises(RuntimeError):
            release_notice.verify_live_version("2.3.3")
        get.return_value.text = "Webapp · Android og iPhone · 2.3.3"
        release_notice.verify_live_version("2.3.3")

    def test_publishes_once_and_records_delivery(self):
        db = Mock()
        ledger = db.collection.return_value.document.return_value
        delivery = {"android": {"accepted": 44, "failed": 0},
                    "pwa": {"accepted": 9, "failed": 0}}
        deliver = Mock(return_value=delivery)
        result = release_notice.publish_notice(NOTICE, db, deliver)
        self.assertEqual(result, delivery)
        deliver.assert_called_once_with(NOTICE["title"], NOTICE["body"], NOTICE["id"], audience="all")
        db.batch.return_value.commit.assert_called_once()
        self.assertEqual(ledger.update.call_args.args[0]["status"], "completed")

    def test_completed_notice_cannot_send_twice(self):
        db = Mock()
        ledger = db.collection.return_value.document.return_value
        ledger.create.side_effect = Conflict("existing")
        ledger.get.return_value.to_dict.return_value = {
            "status": "completed", "manifestSha256": release_notice.notice_digest(NOTICE),
            "delivery": {"pwa": {"accepted": 9}},
        }
        deliver = Mock()
        result = release_notice.publish_notice(NOTICE, db, deliver)
        self.assertEqual(result, {"pwa": {"accepted": 9}})
        deliver.assert_not_called()
        db.batch.assert_not_called()

    def test_partial_failure_is_recorded_without_automatic_replay(self):
        db = Mock()
        ledger = db.collection.return_value.document.return_value
        error = RuntimeError("partial")
        error.delivery = {"pwa": {"accepted": 8, "failed": 1}}
        with self.assertRaises(RuntimeError):
            release_notice.publish_notice(NOTICE, db, Mock(side_effect=error))
        self.assertEqual(ledger.update.call_args.args[0]["status"], "failed")
        self.assertEqual(ledger.update.call_args.args[0]["delivery"], error.delivery)

    @patch("webpush.send_web_announcement")
    def test_release_targets_pwa_without_android_topics(self, send):
        send.return_value = {"targets": 9, "accepted": 9, "failed": 0, "expired": 0}
        delivery = release_notice.send_pwa_notice("Title", "Body", "notice-id", audience="all")
        self.assertEqual(delivery, {"pwa": send.return_value})
        send.assert_called_once_with("Title", "Body", "notice-id", audience="all")

    @patch("webpush.send_web_announcement")
    def test_partial_pwa_failure_stays_visible(self, send):
        send.return_value = {"targets": 9, "accepted": 8, "failed": 1, "expired": 0}
        with self.assertRaises(release_notice.ReleaseDeliveryError) as raised:
            release_notice.send_pwa_notice("Title", "Body", "notice-id", audience="all")
        self.assertEqual(raised.exception.delivery["pwa"]["failed"], 1)


if __name__ == "__main__":
    unittest.main()
