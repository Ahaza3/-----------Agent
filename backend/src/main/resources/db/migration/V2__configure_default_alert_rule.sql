-- 演示环境默认安全阈值：正常负荷约 940MW，三级阈值为 990/1100/1210MW。
INSERT INTO alert_rule (name, type, config, is_active)
SELECT '默认阈值规则', 'THRESHOLD',
       '{"threshold":1100,"redRatio":1.10,"orangeRatio":1.00,"yellowRatio":0.90,"coolingTime":30}',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM alert_rule WHERE name = '默认阈值规则'
);

UPDATE alert_rule
SET config = '{"threshold":1100,"redRatio":1.10,"orangeRatio":1.00,"yellowRatio":0.90,"coolingTime":30}',
    is_active = 1
WHERE name = '默认阈值规则';
