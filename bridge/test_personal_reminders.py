import unittest
from unittest.mock import patch
from contextlib import ExitStack
from datetime import datetime
import webpush


class PersonalReminderTests(unittest.TestCase):
    def run_reminder(self, enabled=True, now=None):
        event = {'id': 'shift', 'dateIso': '2026-10-01', 'time': '18:00', 'description': 'Vakt'}
        subscription = {'name': 'subscriptions/mine', 'fields': {
            'enabled': {'booleanValue': enabled},
            'organizations': {'arrayValue': {'values': []}},
        }}
        reminder = {'name': 'reminders/one', 'fields': {
            'subscriptionId': {'stringValue': 'mine'},
            'organization': {'stringValue': 'A'},
            'eventId': {'stringValue': 'shift'},
            'leadMinutes': {'integerValue': '60'},
        }}
        with ExitStack() as stack:
            values = {
                '_credentials': (object(), 'project'),
                '_list_collection_documents': [reminder],
                '_list_subscription_documents': [subscription, {'name': 'subscriptions/other'}],
                'ensure_vapid_config': {},
                '_get_reminder_state_signature': '',
                '_send_to_subscription': (True, True),
                '_write_reminder_state': None,
            }
            mocks = {name: stack.enter_context(patch.object(webpush, name, return_value=value)) for name, value in values.items()}
            count = webpush.send_due_reminders({'A': [event]}, now=now or datetime(2026, 10, 1, 17, 0, tzinfo=webpush.OSLO))
            return count, mocks['_send_to_subscription']

    def test_explicit_reminder_works_without_general_corps_alerts(self):
        count, send = self.run_reminder()
        self.assertEqual(count, 1)
        send.assert_called_once()
        self.assertEqual(send.call_args.args[0]['name'], 'subscriptions/mine')

    def test_disabled_device_does_not_receive_reminder(self):
        count, send = self.run_reminder(enabled=False)
        self.assertEqual(count, 0)
        send.assert_not_called()

    def test_late_reminder_uses_actual_time_left_and_marks_delay(self):
        count, send = self.run_reminder(now=datetime(2026, 10, 1, 17, 21, tzinfo=webpush.OSLO))
        self.assertEqual(count, 1)
        body = send.call_args.args[1]['body']
        self.assertIn('Påminnelsen kom senere enn planlagt.', body)
        self.assertIn('starter om 39 minutter', body)


if __name__ == '__main__':
    unittest.main()
