(ns status-im.signing.core-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [status-im.signing.core :as signing]
            [status-im.ethereum.abi-spec :as abi-spec]))

(deftest signing-test
  (testing "showing sheet"
    (let [to "0x2f88d65f3cb52605a54a833ae118fb1363acccd2"
          contract "0xc55cf4b03948d7ebc8b9e8bad92643703811d162"
          amount1-hex (str "0x" (abi-spec/number-to-hex "10000000000000000000"))
          amount2-hex (str "0x" (abi-spec/number-to-hex "100000000000000000000"))
          data (abi-spec/encode "transfer(address,uint256)" [to amount2-hex])
          first-tx {:tx-obj {:to  to
                             :from nil
                             :value amount1-hex}}
          second-tx {:tx-obj {:to contract
                              :from nil
                              :data data}}
          sign-first (signing/sign {:db {}} first-tx)
          sign-second (signing/sign sign-first second-tx)]
      (testing "after fist transaction"
        (testing "signing in progress"
          (is (get-in sign-first [:db :signing/tx])))
        (testing "qieue is empty"
          (is (= (get-in sign-first [:db :signing/queue]) '())))
        (testing "first tx object is parsed"
          (is (= (dissoc (get-in sign-first [:db :signing/tx]) :token :hash :cancel?)
                 (merge first-tx
                        {:from {:address nil}
                         :gas nil
                         :gasPrice nil
                         :maxFeePerGas nil
                         :maxPriorityFeePerGas nil
                         :data nil
                         :to      to
                         :contact {:address to}
                         :symbol  :ETH
                         :value   "0x8ac7230489e80000"
                         :amount  "10"})))))
      (testing "after second transaction"
        (testing "signing still in progress"
          (is (get-in sign-second [:db :signing/tx])))
        (testing "queue is not empty, second tx in queue"
          (is (= (get-in sign-second [:db :signing/queue]) (list second-tx))))
        (testing "check queue does nothing"
          (is (not (signing/check-queue sign-second))))
        (let [first-discarded (signing/check-queue (update sign-second :db dissoc :signing/tx))]
          (testing "after first transaction discarded"
            (testing "signing still in progress"
              (is (get-in first-discarded [:db :signing/tx])))
            (testing "qieue is empty"
              (is (= (get-in first-discarded [:db :signing/queue]) '())))
            (testing "second tx object is parsed"
              (is (= (dissoc (get-in first-discarded [:db :signing/tx]) :token :hash :cancel?)
                     (merge second-tx
                            {:from {:address nil}
                             :gas nil
                             :gasPrice nil
                             :maxFeePerGas nil
                             :maxPriorityFeePerGas nil
                             :data    data
                             :to      contract
                             :contact {:address contract}}))))))))))
