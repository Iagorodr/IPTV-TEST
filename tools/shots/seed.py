"""Gera os arquivos de preferências do app para a captura de telas: uma lista M3U de demonstração e o aviso inicial já dispensado."""
import json, html, sys
out = sys.argv[1]
lists = [{"server": "", "user": "", "pass": "", "m3u": "http://10.0.2.2:8099/test-multipais-grande.m3u"}]
acc = '<?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' ?>\n<map>\n  <string name="lists">%s</string>\n</map>\n' % html.escape(json.dumps(lists), quote=True)
usr = '<?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' ?>\n<map>\n  <boolean name="onboarded" value="true" />\n  <string name="plang">pt-BR</string>\n</map>\n'
open(out + "/iptv_account.xml", "w").write(acc)
open(out + "/iptv_user.xml", "w").write(usr)
