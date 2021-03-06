package bitcoin.wallet.sample

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.*

class BalanceFragment : Fragment(), AnkoLogger {

    lateinit var viewModel: MainViewModel
    lateinit var balanceValue: TextView
    lateinit var lastBlockValue: TextView
    lateinit var startButton: Button
    lateinit var generateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)

            viewModel.balance.observe(this, Observer {
                balanceValue.text = (it ?: 0).toString()
            })

            viewModel.lastBlockHeight.observe(this, Observer {
                lastBlockValue.text = (it ?: 0).toString()
            })

            viewModel.status.observe(this, Observer {
                when (it) {
                    MainViewModel.State.STARTED -> {
                        startButton.isEnabled = false
                    }
                    else -> {
                        startButton.isEnabled = true
                    }
                }
            })

        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_balance, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        balanceValue = view.findViewById(R.id.balanceValue)
        lastBlockValue = view.findViewById(R.id.lastBlockValue)
        startButton = view.findViewById(R.id.buttonStart)
        generateButton = view.findViewById(R.id.buttonGenerate)

        startButton.setOnClickListener {
            viewModel.start()
        }

        generateButton.setOnClickListener {
            viewModel.generateNewMnemonic()
            toast(viewModel.getWords().joinToString(separator = " ") + " " + viewModel.receiveAddress())
        }
    }
}
