(ns awesm.storm.bolt-test
  (:use [clojure test])
  (:import [awesm.storm.spout RandomClickEventSpout])
  (:import [awesm.storm.bolt BotFlagBolt ReferrerBolt GeoBolt])
  (:use [backtype.storm bootstrap testing])
  (:use [backtype.storm.daemon common])
  )

(bootstrap)

(deftest test-botflagbolt
  (doseq [zmq-on? [false false]]
    (with-simulated-time-local-cluster [cluster :supervisors 4
                                        :daemon-conf {STORM-LOCAL-MODE-ZMQ zmq-on?}]
    (let [topology (thrift/mk-topology
      {"1" (thrift/mk-spout-spec (RandomClickEventSpout.))}
      {"2" (thrift/mk-bolt-spec {"1" :shuffle} (BotFlagBolt.) :parallelism-hint 1)
       })
          results (complete-topology cluster
              topology
              :mock-sources {"1" [["{'http_user_agent': 'Mozilla/5.0 (compatible; MSIE 6.0; Windows NT 5.1)', 'bot_flag': '0'}"]
                                  ["{'http_user_agent': 'Mozilla/5.0 (compatible; MSIE 6.0; Windows NT 5.1)', 'bot_flag': '1'}"]
                                  ["{'http_user_agent': 'AppEngine-Google', 'bot_flag': '0'}"]]}
              :storm-conf {TOPOLOGY-DEBUG true
                                TOPOLOGY-WORKERS 2})]
      (is (ms= [["{'http_user_agent': 'Mozilla/5.0 (compatible; MSIE 6.0; Windows NT 5.1)', 'bot_flag': '0'}"]
                ["{'http_user_agent': 'Mozilla/5.0 (compatible; MSIE 6.0; Windows NT 5.1)', 'bot_flag': '1'}"]
                ["{'http_user_agent': 'AppEngine-Google', 'bot_flag': '0'}"]]
            (read-tuples results "1")))
      (is (ms= [["{\"http_user_agent\":\"Mozilla\\/5.0 (compatible; MSIE 6.0; Windows NT 5.1)\",\"bot_flag\":\"0\",\"new_bot_flag\":\"0\"}"]
                ["{\"http_user_agent\":\"Mozilla\\/5.0 (compatible; MSIE 6.0; Windows NT 5.1)\",\"bot_flag\":\"1\",\"new_bot_flag\":\"1\"}"]
                ["{\"http_user_agent\":\"AppEngine-Google\",\"bot_flag\":\"0\",\"new_bot_flag\":\"1\"}"]]
            (read-tuples results "2")))
      ))))

(deftest test-referrerbolt
  (doseq [zmq-on? [false false]]
    (with-simulated-time-local-cluster [cluster :supervisors 4
                                        :daemon-conf {STORM-LOCAL-MODE-ZMQ zmq-on?}]
      (let [topology (thrift/mk-topology
        {"1" (thrift/mk-spout-spec (RandomClickEventSpout.))}
        {"2" (thrift/mk-bolt-spec {"1" :shuffle} (ReferrerBolt.) :parallelism-hint 1)
         })
            results (complete-topology cluster
              topology
              :mock-sources {"1" [["{'http_referer': 'http://test.awe.sm/zomg.php?query=val#anchor'}"]
                                  ["{'http_referer': null}"]]}
              :storm-conf {TOPOLOGY-DEBUG true
                           TOPOLOGY-WORKERS 2})]
        (is (ms= [["{'http_referer': 'http://test.awe.sm/zomg.php?query=val#anchor'}"]
                  ["{'http_referer': null}"]]
              (read-tuples results "1")))
        (is (ms= [["{\"http_referer\":\"http://test.awe.sm/zomg.php?query=val#anchor\",\"http_referer_domain\":\"http://test.awe.sm\"}"]
                  ["{\"http_referer\":null,\"http_referer_domain\":null}"]]
              (read-tuples results "2")))
        ))))

(deftest test-geobolt
  (doseq [zmq-on? [false false]]
    (with-simulated-time-local-cluster [cluster :supervisors 4
                                        :daemon-conf {STORM-LOCAL-MODE-ZMQ zmq-on?}]
      (let [topology (thrift/mk-topology
        {"1" (thrift/mk-spout-spec (RandomClickEventSpout.))}
        {"2" (thrift/mk-bolt-spec {"1" :shuffle} (GeoBolt.) :parallelism-hint 1)
         })
            results (complete-topology cluster
          topology
          :mock-sources {"1" [["{'remote_addr': '151.38.39.114'}"]
                              ["{'remote_addr': '64.81.104.131'}"]]}
          :storm-conf {TOPOLOGY-DEBUG true
                       TOPOLOGY-WORKERS 2})]
        (is (ms= [["{'remote_addr': '151.38.39.114'}"]
                  ["{'remote_addr': '64.81.104.131'}"]]
              (read-tuples results "1")))
        (is (ms= [["{\"remote_addr\":\"151.38.39.114\",\"geo_country_code\":\"IT\",\"geo_country\":\"Italy\",\"geo_region\":null,\"geo_city\":null}"]
                  ["{\"remote_addr\":\"64.81.104.131\",\"geo_country_code\":\"US\",\"geo_country\":\"United States\",\"geo_region\":\"CA\",\"geo_city\":\"Los Angeles\"}"]]
              (read-tuples results "2")))
        ))))