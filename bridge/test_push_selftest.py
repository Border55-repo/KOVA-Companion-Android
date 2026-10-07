import unittest
from datetime import datetime, timezone, timedelta
from unittest.mock import Mock, patch
import push_selftest as target
from push import change_summary


class SelfTestTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 10, 7, 12, tzinfo=timezone.utc)
        self.request = {"name": "projects/p/databases/(default)/documents/webPushTests/a", "updateTime": "v1", "fields": {
            "requestedAt": {"timestampValue": self.now.isoformat()},
            "reminderToken": {"stringValue": "private-token"}}}
        self.subscription = {"name": "subscriptions/a", "fields": {
            "enabled": {"booleanValue": True}, "reminderToken": {"stringValue": "private-token"}}}

    def test_rejects_wrong_device_token_disabled_expired_and_processed(self):
        self.assertTrue(target.eligible(self.request, self.subscription, self.now))
        self.assertFalse(target.eligible(self.request, {}, self.now))
        self.assertFalse(target.eligible(self.request, self.subscription, self.now + timedelta(minutes=31)))
        self.request['fields']['processedRequest'] = {'stringValue': self.now.isoformat()}
        self.assertFalse(target.eligible(self.request, self.subscription, self.now))
        del self.request['fields']['processedRequest']
        self.subscription['fields']['enabled']['booleanValue'] = False
        self.assertFalse(target.eligible(self.request, self.subscription, self.now))
        self.subscription['fields']['enabled']['booleanValue'] = True
        self.request['fields']['reminderToken']['stringValue'] = 'wrong'
        self.assertFalse(target.eligible(self.request, self.subscription, self.now))

    def test_claims_exact_device_before_sending_and_never_broadcasts(self):
        with patch.object(target, '_credentials', return_value=(Mock(), 'p')), \
             patch.object(target, '_list_collection_documents', return_value=[self.request]), \
             patch.object(target, '_list_subscription_documents', return_value=[self.subscription, {'name':'subscriptions/b'}]), \
             patch.object(target, 'ensure_vapid_config', return_value={}), \
             patch.object(target.requests, 'patch') as claim, \
             patch.object(target, '_send_to_subscription', return_value=(True, True)) as send:
            claim.return_value.status_code = 200
            self.assertEqual(target.send_push_selftests(self.now), 1)
            self.assertEqual(send.call_args.args[0], self.subscription)
            self.assertEqual(send.call_args.args[1]['kind'], 'test')
            self.assertEqual(claim.call_args.kwargs['params']['currentDocument.updateTime'], 'v1')
            send.reset_mock()
            claim.return_value.status_code = 412
            self.assertEqual(target.send_push_selftests(self.now), 0)
            send.assert_not_called()

    def test_change_summary_includes_all_changed_fields(self):
        old = dict(dateIso='2026-10-07', dateLabel='7.10', time='18:00', type='Vakt', description='A')
        new = dict(old, time='19:00', type='Øvelse', description='B')
        summary = change_summary(old, new)
        for expected in ['Tid: 18:00 → 19:00', 'Type: Vakt → Øvelse', 'Beskrivelse: A → B']:
            self.assertIn(expected, summary)
        self.assertNotIn('Dato:', summary)
